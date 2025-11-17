library(ggplot2)
library(dplyr)
library(tmap)
library(matsim)
library(sf)

# Read a csv file with following header: linkId,from_time,to_time,length,occupancy,initial
read_parking_occupancy <- function(path) {
  df <- read.csv(path, stringsAsFactors = FALSE)
  # add saturation level columns
  df <- add_saturation_level(df)
  return(df)
}

# add a column with the saturation level. Take as parameter scale_factor and car_length
add_saturation_level <- function(parking_occupancy, scale_factor = 0.1, car_length = 7.5) {
  parking_occupancy %>%
    mutate(
      capacity = (length / car_length) * scale_factor,
      saturation_relative = occupancy / capacity,
    ) %>%
    mutate(
      saturation_absolute = occupancy - capacity
    )
}

# Custom theme: increase font size for all text elements.
# increase: multiplier for base font size (e.g. 1.5 = 50% larger)
# base_size: base font size to multiply
theme_increase_all_text <- function(increase = 1.5, base_size = 11, base_family = "") {
  theme_minimal(base_size = base_size * increase, base_family = base_family) +
    theme(
      plot.title = element_text(size = rel(1.2)),
      axis.title = element_text(size = rel(1.0)),
      axis.text = element_text(size = rel(0.95)),
      legend.title = element_text(size = rel(1.0)),
      legend.text = element_text(size = rel(0.95)),
      strip.text = element_text(size = rel(1.0)),
      # ensure default text element is also scaled
      text = element_text(size = base_size * increase)
    )
}

# plotting functions: each takes two dataframes (base and policy) and shows both in one plot
plot_relative_vs_initial <- function(combined, theme_increase = 1.5) {
  p1 <- ggplot(combined, aes(x = initial, y = saturation_relative, color = scenario)) +
    geom_point(alpha = 0.6) +
    labs(
      title = "Parking Saturation Relative vs Initial Occupancy",
      x = "Initial Occupancy",
      y = "Saturation Relative",
      color = "Scenario"
    ) +
    theme_increase_all_text(increase = theme_increase)

  p2 <- ggplot(combined, aes(x = capacity, y = saturation_relative, color = scenario)) +
    geom_point(alpha = 0.6) +
    labs(
      title = "Parking Saturation Relative vs Capacity",
      x = "Capacity",
      y = "Saturation Relative",
      color = "Scenario"
    ) +
    theme_increase_all_text(increase = theme_increase)

  p3 <- ggplot(combined, aes(x = initial, y = saturation_absolute, color = scenario)) +
    geom_point(alpha = 0.6) +
    geom_abline(slope = 0, intercept = 0, color = "red", linetype = "dashed") + # zero-line for absolute saturation
    labs(
      title = "Parking Saturation Absolute vs Initial Occupancy",
      x = "Initial Occupancy",
      y = "Saturation Absolute",
      color = "Scenario"
    ) +
    theme_increase_all_text(increase = theme_increase)

  return(list(p1 = p1, p2 = p2, p3 = p3))
}

# Read parking occupancy data for both base and policy and create the three comparison plots
parking_occupancy_base <- read_parking_occupancy("./assets/parking_occupancy_base.csv")
parking_occupancy_policy <- read_parking_occupancy("./assets/parking_occupancy_autofrei.csv")

parking_occupancy_base$scenario <- "base"
parking_occupancy_policy$scenario <- "policy"

combined <- bind_rows(parking_occupancy_base, parking_occupancy_policy)

# plots <- plot_relative_vs_initial(combined, "base", "policy")
#
# print(plots$p1)
# print(plots$p2)
# print(plots$p3)

# Make a diff plot for absolute saturation between base (x-axis) and policy (y-axis) (no function)
diff_df <- parking_occupancy_base %>%
  select(linkId, saturation_absolute) %>%
  rename(saturation_absolute_base = saturation_absolute) %>%
  inner_join(
    parking_occupancy_policy %>%
      select(linkId, saturation_absolute) %>%
      rename(saturation_absolute_policy = saturation_absolute),
    by = "linkId"
  )
diff_plot <- ggplot(diff_df, aes(x = saturation_absolute_base, y = saturation_absolute_policy)) +
  geom_point(alpha = 0.6) +
  labs(
    title = "Parking Saturation Absolute: Base vs Policy",
    x = "Saturation Absolute Base",
    y = "Saturation Absolute Policy"
  ) +
  theme_increase_all_text(increase = 1.5)
# print(diff_plot)

# plot a tmap
network <- matsim::read_network("./assets/berlin-v6.4.output_network.xml.gz")

links_sf <- network$links %>%
  # 1) Filter out ids starting with "pt"
  filter(!grepl("^pt", id)) %>%
  select(id, x.from, y.from, x.to, y.to) %>%
  rowwise() %>%
  mutate(
    geometry = list(
      st_linestring(
        matrix(c(x.from, y.from, x.to, y.to), ncol = 2, byrow = TRUE)
      )
    )
  ) %>%
  ungroup() %>%
  st_as_sf(crs = 25832)

links_sf <- st_transform(links_sf, crs = 4326)

# join with combined data
links_sf_combined <- links_sf %>%
  left_join(
    combined %>%
      filter(!is.na(saturation_absolute)) %>%
      select(linkId = linkId, saturation_absolute, saturation_relative, scenario),
    by = c("id" = "linkId")
  )

tmap_mode("view")
tm_shape(links_sf_combined) +
  tm_lines(
    col = "saturation_absolute",
    lwd = 2,
    palette = "-RdYlGn",
    midpoint = 0,
    breaks = seq(-20, 50, by = 5)
    # title = "Saturation Absolute"
  ) +
  tm_facets(by = "scenario") +
  tm_layout(
    title = "Parking Saturation Absolute by Scenario",
    legend.outside = TRUE
  )
