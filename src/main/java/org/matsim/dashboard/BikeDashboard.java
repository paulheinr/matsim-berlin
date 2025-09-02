package org.matsim.dashboard;

import org.matsim.application.analysis.traffic.TrafficAnalysis;
import org.matsim.application.prepare.network.CreateAvroNetwork;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.ColorScheme;
import org.matsim.simwrapper.viz.MapPlot;

import java.awt.*;
import java.io.File;
import java.nio.file.Paths;
import java.util.Map;

import static java.util.Map.*;


/**
 * Dashboard for analyzing bike-related data in MATSim scenarios.
 * This dashboard will provide insights into cycling patterns, infrastructure usage, and other bike-related metrics.
 * Used for the CRBAM workshop in 2025.
 */

public class BikeDashboard implements Dashboard {

	@Override
	public void configure(Header header, Layout layout) {
		header.title = "Cycling Research Board 2025";
		header.description = "Dashboard for analyzing bike-related data in Berlin.";
		/*
		layout.row("bike-network-map").el(MapPlot.class, (viz, data) -> {
			viz.title = "Bike network";
			viz.center = data.context().getCenter();
			viz.zoom = data.context().mapZoomLevel;
			viz.setShape(
				data.compute(CreateAvroNetwork.class, "network.avro", "--with-properties"),
				"id"
			);
			viz.height = 12d;
			viz.display.lineColor.columnName = "cycleway";
		}); */

		layout.row("bike-perceived-safety-map").el(MapPlot.class, (viz, data) -> {
			viz.title = "Perceived Safety Scores";
			viz.center = data.context().getCenter();
			viz.zoom = data.context().mapZoomLevel;
			// Use the shapefile path as a relative path ONLY:
			viz.setShape("../shp/psafeUrbanLinksBerlin_v1.shp");
			viz.width = 16d;
			viz.height = 12d;
			viz.addDataset("psafeUrbanLinksBerlin_v1.shp", "../shp/psafeUrbanLinksBerlin_v1.shp");
			viz.display.lineColor.dataset = "psafeUrbanLinksBerlin_v1.shp";
			viz.display.lineColor.columnName = "LevPsafeeb";
			viz.display.lineWidth.columnName = "LevPsafeeb";
			viz.display.lineColor.setColorRamp(ColorScheme.Viridis,  6, true, "2=red,3=orange,4=yellow,5=green,6=blue");

		});

		layout.row("bike-volumes").el(MapPlot.class, (viz, data) -> {
			viz.title = "Bike volumes on main roads";
			viz.center = data.context().getCenter();
			viz.zoom = data.context().mapZoomLevel;
			viz.setShape("../dataBikeVolumes/testWitheNewCoord.shp");
			viz.width = 16d;
			viz.height = 12d;
			viz.addDataset("testWitheNewCoord.shp", "../dataBikeVolumes/testWitheNewCoord.shp");
			viz.display.lineColor.dataset = "testWitheNewCoord.shp";
			viz.display.lineWidth.dataset = "testWitheNewCoord.shp";
			viz.display.lineWidth.columnName="strklasse1";
			viz.display.lineWidth.scaleFactor = 500.;
			viz.display.lineColor.columnName = "dtvw_rad";
			viz.display.lineColor.setColorRamp(ColorScheme.Viridis,  5, true);
		});

		layout.row("Reported incidents in Berlin").el(MapPlot.class, (viz, data) -> {
			viz.title = "Reported incidents in Berlin";
			viz.center = data.context().getCenter();
			viz.zoom = data.context().mapZoomLevel;
			viz.setShape("../simra/Berlin-incidents.json");
			viz.height = 12d;
			viz.addDataset("Berlin-incidents.json", "../simra/Berlin-incidents.json");
			viz.display.fill.dataset = "Berlin-incidents.json";
			viz.display.fill.columnName = "scary";
			viz.display.fill.setColorRamp(ColorScheme.Viridis,  2, true);
			//viz.display.lineColor.setColorRamp(ColorScheme.Viridis,  5, true);
		});

	}

}


