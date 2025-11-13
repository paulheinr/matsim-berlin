library(matsim)

library(xml2)
library(dplyr)
library(htmlwidgets)

library(dplyr)
library(leaflet)
library(sf)

read_vehicle_leaves_traffic <- function(path) {
  # Read the XML (compressed .gz works automatically)
  doc <- read_xml(path)

  # Select only "vehicle leaves traffic" events
  ev <- xml_find_all(doc, ".//event[@type='vehicle leaves traffic']")
  # Build tibble
  tibble(
    time = as.numeric(xml_attr(ev, "time")),
    person = xml_attr(ev, "person"),
    link = xml_attr(ev, "link"),
    mode = xml_attr(ev, "networkMode")
  )
}

build_parking_summary <- function(parking_events) {
  parking_events %>%
    group_by(link, mode) %>%
    summarise(num_events = n(), .groups = "drop")
}

# Read parking events from both scenarios
parking_events_base <- read_vehicle_leaves_traffic("src/main/R/assets/filtered_parking_base.xml.gz")
parking_events_autofrei <- read_vehicle_leaves_traffic("src/main/R/assets/filtered_parking_autofrei.xml.gz")

# group by link and mode, count number of events
parking_summary_base <- build_parking_summary(parking_events_base)
parking_summary_autofrei <- build_parking_summary(parking_events_autofrei)

# print number of parking events
cat("Number of parking events in base scenario:", nrow(parking_events_base), "\n")
cat("Number of parking events in autofrei scenario:", nrow(parking_events_autofrei), "\n")

network <- matsim::read_network("src/main/R/assets/berlin-v6.4.output_network.xml.gz")

# calculate the difference in number of parking events per link between the two scenarios
parking_comparison <- parking_summary_base %>%
  full_join(parking_summary_autofrei, by = c("link", "mode"), suffix = c("_base", "_autofrei")) %>%
  mutate(
    num_events_base = ifelse(is.na(num_events_base), 0, num_events_base),
    num_events_autofrei = ifelse(is.na(num_events_autofrei), 0, num_events_autofrei),
    diff_events = num_events_autofrei - num_events_base,
    diff_relative = ifelse(num_events_base == 0, NA, (num_events_autofrei - num_events_base) / num_events_base)
  )

# print distribution of differences
cat("Distribution of absolute differences in parking events (autofrei - base):\n")
print(summary(parking_comparison$diff_events))

# show the links on a map with color indicating the difference in parking events
# links have x.to and y.to resp. x.from and y.from coordinates
# coordinate system is epsg:25832
links_sf <- network$links %>%
  # 1) Filter out ids starting with "pt"
  filter(!grepl("^pt", id)) %>%
  select(id, x.from, y.from, x.to, y.to) %>%
  left_join(parking_comparison, by = c("id" = "link")) %>%
  rowwise() %>%
  mutate(
    geometry = list(
      st_linestring(
        matrix(c(x.from, y.from, x.to, y.to), ncol = 2, byrow = TRUE)
      )
    )
  ) %>%
  ungroup() %>%
  # scale relative difference to percent
  mutate(diff_relative = diff_relative * 100) %>%
  st_as_sf(crs = 25832)

# links_sf <- st_simplify(links_sf, dTolerance = 1)
# transform to WGS84 for leaflet
links_sf <- st_transform(links_sf, crs = 4326)

# filter out features with NA diff_relative (don't show them) and where parking is completely removed (diff_relative == -100)
links_sf_visible <- links_sf %>%
  filter(!is.na(diff_relative)) %>%
  filter(diff_relative != -100)

library(tmap)
tmap_mode("view")

tm_shape(links_sf_visible) +
  tm_lines(
    col = "diff_relative",
    palette = "-RdYlGn",
    breaks = seq(-500, 500, by = 50),
    # title = "Relative Difference (Autofrei - Base) (%)",
    lwd = 2,
    legend.show = TRUE,
    popup.vars = c(
      "Base events" = "num_events_base",
      "Autofrei events" = "num_events_autofrei",
      "Diff events" = "diff_events",
      "Diff relative (%)" = "diff_relative"
    )
  ) +
  tm_layout(legend.outside = TRUE)

# determine negative and positive ranges (diff_relative is already in percent)
# min_val <- min(links_sf_visible$diff_relative, na.rm = TRUE)
# max_val <- max(links_sf_visible$diff_relative, na.rm = TRUE)
#
# # continuous palettes: negative side green -> gray, positive side gray -> red
# pal_neg <- if (!is.na(min_val) && min_val < 0) {
#   colorNumeric(
#     palette = colorRampPalette(c("green", "gray"))(100),
#     domain = c(min_val, 0)
#   )
# } else {
#   NULL
# }
#
# pal_pos <- if (!is.na(max_val) && max_val > 0) {
#   max_cap <- min(max_val, 500)  # cap positive scale at 500%
#   base_pal <- colorNumeric(
#     palette = colorRampPalette(c("gray", "red"))(100),
#     domain = c(0, max_cap)
#   )
#   # clamp values above max_cap so they receive the same color as max_cap
#   function(x) base_pal(pmin(x, max_cap))
# } else {
#   NULL
# }
#
# # combined palette function for leaflet (returns NA for NA input)
# pal_combined <- function(x) {
#   sapply(x, function(v) {
#     if (is.na(v)) return(NA_character_)
#     if (v < 0 && !is.null(pal_neg)) return(pal_neg(v))
#     if (v >= 0 && !is.null(pal_pos)) return(pal_pos(v))
#     "#808080"
#   })
# }
#
# l <- leaflet(links_sf_visible) %>%
#   addTiles() %>%
#   addPolylines(
#     color = ~pal_combined(diff_relative),
#     weight = 5,
#     opacity = 0.8,
#     popup = ~paste(
#       "Link ID:", id, "<br>",
#       "Mode:", mode, "<br>",
#       "Base events:", num_events_base, "<br>",
#       "Autofrei events:", num_events_autofrei, "<br>",
#       "Diff events:", diff_events, "<br>",
#       "Diff relative:", round(diff_relative, 2), "%"
#     )
#   )
#
# # add separate continuous legends for negative and positive ranges
# if (!is.null(pal_neg)) {
#   l <- l %>% addLegend(
#     position = "bottomleft",
#     pal = pal_neg,
#     values = seq(min_val, 0, length.out = 5),
#     title = "Relative Difference (Autofrei - Base)\nDecrease (%)",
#     opacity = 1,
#     labFormat = labelFormat(suffix = "%")
#   )
# }
#
# if (!is.null(pal_pos)) {
#   l <- l %>% addLegend(
#     position = "bottomright",
#     pal = pal_pos,
#     values = seq(0, max_val, length.out = 5),
#     title = "Relative Difference (Autofrei - Base)\nIncrease (%)",
#     opacity = 1,
#     labFormat = labelFormat(suffix = "%")
#   )
# }
#
# saveWidget(l, "map.html", selfcontained = FALSE)