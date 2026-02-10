package org.matsim.analysis;

import org.matsim.application.MATSimAppCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "air-pollution-hbefa-types", description = "Run AirPollutionAnalysis with different combinations of HBEFA vehicle types.")
public class RunAirpollutionAnalysisWithDifferentHbefaVehicleTypes implements MATSimAppCommand {
	@Override
	public Integer call() throws Exception {
//		TODO: different hbefa types should be implemented via map run param.
//		we only need to run 100% BEV and then have to interpolate between that and the required
//		we need:
//		1) 90% BEV, "normal", 5% H2
//		2) 60% BEV, 1% "normal", 20% synthetische Kraftstoffe, 19% H2
//		TODO: change all private vehicles to hbefa BEV

		return 0;
	}
}
