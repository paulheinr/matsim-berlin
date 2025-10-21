package org.matsim.run.gartenfeld;

import com.google.inject.Inject;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.router.MultimodalLinkChooser;
import org.matsim.core.router.MultimodalLinkChooserDefaultImpl;
import org.matsim.core.router.RoutingRequest;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.run.OpenBerlinScenario;

/**
 * Link chooser for the Gartenfeld scenario which forces agents to use a designated parking garage link when using a car.
 */
class GartenfeldLinkChooser implements MultimodalLinkChooser {
	// deliberately not public

//	TODO: which link is access and which egress? in the methods below only one of them is used for both access and egress
	static final Id<Link> accessLink = Id.createLinkId("network-DNG.1_r");

	static final Id<Link> egressLink = Id.createLinkId("network-DNG.1");

	@Inject private MultimodalLinkChooserDefaultImpl delegate;

	/**
	 * Geometry of the Gartenfeld area.
	 */
	private final Geometry area;

	public GartenfeldLinkChooser(ShpOptions area) {
		this.area = area.getGeometry(OpenBerlinScenario.CRS);
	}

	@Override
	public Link decideAccessLink(RoutingRequest routingRequest, String mode, Network network) {
		if ( area.contains(MGC.coord2Point(routingRequest.getFromFacility().getCoord())) && TransportMode.car.equals( mode ) ){
			return network.getLinks().get( Id.createLinkId( "network-DNG.1" ) );
		} else {
			return delegate.decideAccessLink( routingRequest, mode, network );
		}
	}

	@Override
	public Link decideEgressLink(RoutingRequest routingRequest, String mode, Network network) {
		if ( area.contains(MGC.coord2Point(routingRequest.getToFacility().getCoord())) && TransportMode.car.equals( mode ) ){
			return network.getLinks().get( Id.createLinkId( "network-DNG.1" ) );
		} else {
			return delegate.decideEgressLink( routingRequest, mode, network ) ;
		}
	}
}
