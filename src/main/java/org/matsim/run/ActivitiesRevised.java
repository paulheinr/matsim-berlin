package org.matsim.run;

import org.matsim.core.config.Config;
import org.matsim.core.config.groups.ScoringConfigGroup;

/**
 * Defines available activity types.
 */
public enum ActivitiesRevised{
	home,
	other,
	outside_recreation,
	transport,
	visit,
	edu_kiga,
	edu_primary,
	edu_secondary,
	edu_higher,
	edu_other,

	work,
	work_business,
	personal_business,
	leisure,
	dining,
	shop_daily,
	shop_other,

	// Commercial traffic types
	service;

	/**
	 * Start time of an activity in hours, can be -1 if not defined.
	 */
//	private final double start;

	/**
	 * End time of an activity in hours, can be -1 if not defined.
	 */
//	private final double end;

//	ActivitiesRevised( double start, double end ) {
//		this.start = start;
//		this.end = end;
//	}

	ActivitiesRevised() {
//		this.start = -1;
//		this.end = -1;
	}


	/**
	 * Apply start and end time to params.
	 */
	public ScoringConfigGroup.ActivityParams apply(ScoringConfigGroup.ActivityParams params) {
//		if (start >= 0)
//			params = params.setOpeningTime(start * 3600.);
//		if (end >= 0)
//			params = params.setClosingTime(end * 3600.);

		return params;
	}

	/**
	 * Add required activity params for the scenario.
	 */
	public static void replaceActivityParams( Config config ) {
		config.scoring().getScoringParameters( null ).getActivityParams().clear();
		config.scoring().getScoringParameters( ScoringConfigGroup.DEFAULT_SUBPOPULATION ).getActivityParams().clear();
		// (yy would be better to do this by subpop, but I think that we are still using the "default" subpop.)

		for ( ActivitiesRevised value : ActivitiesRevised.values()) {
			// Default length if none is given
			config.scoring().addActivityParams(value.apply(new ScoringConfigGroup.ActivityParams(value.name())).setTypicalDuration(6 * 3600));
			config.scoring().addActivityParams(value.apply(new ScoringConfigGroup.ActivityParams(createMorningActivityType( value.name() ))).setTypicalDuration(6 * 3600));
			config.scoring().addActivityParams(value.apply(new ScoringConfigGroup.ActivityParams(createEveningActivityType( value.name() ))).setTypicalDuration(6 * 3600));

			for (long ii = 600; ii <= 97200; ii += 600) {
				config.scoring().addActivityParams(value.apply(new ScoringConfigGroup.ActivityParams(value.name() + "_" + ii).setTypicalDuration(ii)));
				config.scoring().addActivityParams(value.apply(new ScoringConfigGroup.ActivityParams(createMorningActivityType( value.name() ) + "_" + ii).setTypicalDuration(ii)));
				config.scoring().addActivityParams(value.apply(new ScoringConfigGroup.ActivityParams(createEveningActivityType( value.name() ) + "_" + ii).setTypicalDuration(ii)));
			}
		}

		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("commercial_start").setTypicalDuration(3600));
		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("commercial_end").setTypicalDuration(3600));

		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("freight_start").setTypicalDuration(3600));
		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("freight_end").setTypicalDuration(3600));

	}
	public static String createMorningActivityType( String baseActType ) {
		return baseActType + "_morning";
	}
	public static String createEveningActivityType( String baseActType ) {
		return baseActType + "_evening";
	}

}
