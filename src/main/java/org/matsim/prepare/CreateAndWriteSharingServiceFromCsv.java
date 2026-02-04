package org.matsim.prepare;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.CrsOptions;
import org.matsim.application.options.CsvOptions;
import org.matsim.contrib.shared_mobility.io.*;
import org.matsim.contrib.shared_mobility.service.SharingStation;
import org.matsim.contrib.shared_mobility.service.SharingVehicle;
import org.matsim.core.config.groups.NetworkConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.filter.NetworkFilterManager;
import org.matsim.core.utils.io.IOUtils;
import picocli.CommandLine;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;

import java.util.HashMap;
import java.util.Map;

@CommandLine.Command(name = "create-sharing-service", description = "Create a sharing service with stations and vehicles based on station data from a csv file.")
public class CreateAndWriteSharingServiceFromCsv implements MATSimAppCommand {
	private static final Logger log = LogManager.getLogger(CreateAndWriteSharingServiceFromCsv.class);

	@CommandLine.Option(names = "--sharing-stations", description = "File with coordinates of sharing stations.", required = true)
	private String sharingStationsCsv;
	@CommandLine.Option(names = "--x-coord-column", description = "Name of column with x coords in sharingStationsCsv.", defaultValue = "xcoord")
	private String xCoordColumnName;
	@CommandLine.Option(names = "--y-coord-column", description = "Name of column with y coords in sharingStationsCsv.", defaultValue = "ycoord")
	private String yCoordColumnName;
	@CommandLine.Option(names = "--id-column", description = "Name of column with id in sharingStationsCsv.", defaultValue = "name")
	private String idColumnName;
	@CommandLine.Option(names = "--network", description = "Path to network file.", required = true)
	private String networkPath;
	@CommandLine.Option(names = "--vehicles", description = "How many sharing vehicles should be provided per sharing station", defaultValue = "10")
	private Integer sharingVehiclesPerStation;
	@CommandLine.Option(names = "--capacity", description = "The capacity of sharing vehicles each station is assigned.", defaultValue = "1000")
	private Integer sharingStationCapacity;
	@CommandLine.Option(names = "--output", description = "Path to output service xml file.", required = true)
	private String output;
	@CommandLine.Mixin
	private CrsOptions crs;

	public static void main(String[] args) {
		new CreateAndWriteSharingServiceFromCsv().execute(args);
	}

	@Override
	public Integer call() throws Exception {
//		we want to ignore pt links and nodes
		Network network = NetworkUtils.readNetwork(networkPath);
		NetworkFilterManager manager = new NetworkFilterManager(network, new NetworkConfigGroup());
		manager.addLinkFilter(link -> !link.getId().toString().contains("pt_"));
		manager.addNodeFilter(node -> !node.getId().toString().contains("pt_"));
		network = manager.applyFilters();

		Table stations = Table.read().csv(CsvReadOptions.builder(IOUtils.getBufferedReader(sharingStationsCsv))
			.columnTypesPartial(Map.of(idColumnName, ColumnType.TEXT, xCoordColumnName, ColumnType.DOUBLE, yCoordColumnName, ColumnType.DOUBLE))
			.sample(false)
			.separator(CsvOptions.detectDelimiter(sharingStationsCsv))
			.build());

		log.info("Found {} sharing stations in {}.", stations.rowCount(), sharingStationsCsv);

		SharingServiceSpecification service = new DefaultSharingServiceSpecification();

//		we need a map of stationId to linkId because we cannot retrieve the link of the station from the SharingServiceSpecification later
		Map<Id<SharingStation>, Id<Link>> stationLinks = new HashMap<>();

//		create stations based on coords from csv file
		int i = 0;
		for (Row row : stations) {
			Id<SharingStation> id = Id.create(row.getText("name") + "_" + i, SharingStation.class);
			double xCoord = row.getDouble(xCoordColumnName);
			double yCoord = row.getDouble(yCoordColumnName);

			Coord coord = new Coord(xCoord, yCoord);
			if (crs.getInputCRS() != null && crs.getTargetCRS() != null && !crs.getInputCRS().equals(crs.getTargetCRS())) {
				coord = crs.getTransformation().transform(coord);
			}

			Link link = NetworkUtils.getNearestLink(network, coord);

			service.addStation(ImmutableSharingStationSpecification.newBuilder()
				.id(id)
				.capacity(sharingStationCapacity)
				.linkId(link.getId())
				.build());

			stationLinks.put(id, link.getId());
			i++;
		}

//		create sharing vehicles
		for (Map.Entry<Id<SharingStation>, Id<Link>> entry : stationLinks.entrySet()) {
			for (int j = 0; j < sharingVehiclesPerStation; j++) {
				Id<SharingVehicle> vehicleId = Id.create(entry.getKey() + "_" + j, SharingVehicle.class);

				service.addVehicle(ImmutableSharingVehicleSpecification.newBuilder()
					.id(vehicleId)
					.startStationId(entry.getKey())
					.startLinkId(entry.getValue())
					.build());
			}
		}
		new SharingServiceWriter(service).write(output);
		log.info("Sharing service with stations and vehicles written to {}.", output);

		return 0;
	}
}
