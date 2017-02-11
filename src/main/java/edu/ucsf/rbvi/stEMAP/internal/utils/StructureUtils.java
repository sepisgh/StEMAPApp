package edu.ucsf.rbvi.stEMAP.internal.utils;

import java.awt.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.View;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.work.TaskMonitor;

import edu.ucsf.rbvi.stEMAP.internal.model.StEMAPManager;

public class StructureUtils {

	public static Map<Color, Set<String>> getResiduesAndColors(StEMAPManager mgr, CyNetworkView netView, 
	                                                           CyNode node) {
		Map<Color, Set<String>> colorMap = new HashMap<>();

		CyNetwork net = netView.getModel();
		for (CyEdge edge: net.getAdjacentEdgeList(node, CyEdge.Type.ANY)) {
			Double weight = net.getRow(edge).get(ModelUtils.WEIGHT_COLUMN, Double.class);
			CyNode resNode = edge.getSource();
			if (edge.getSource().equals(node)) {
				resNode = edge.getTarget();
			}
			String residues = net.getRow(resNode).get(ModelUtils.RESIDUE_COLUMN, String.class);
			if (residues != null && residues.length() > 0) {
				List<String> resSet = StructureUtils.getResidue(mgr, net, resNode);
				if (mgr.ignoreMultiples()) {
					String mutType = net.getRow(resNode).get(ModelUtils.MUT_TYPE_COLUMN, String.class);
					if (mutType != null && (mutType.equals("del") || mutType.equals("multiple")))
						continue;
				}

				Color color;
				if (weight > 0.0) {
					color = (Color) mgr.getEColorScale().getPaint(weight*mgr.getScale());
				} else {
					color = (Color) mgr.getSColorScale().getPaint(Math.abs(weight)*mgr.getScale());
				}
				if (!colorMap.containsKey(color))
					colorMap.put(color, new HashSet<>());

				// System.out.println("Adding "+residue+" to "+color);
				colorMap.get(color).addAll(resSet);
			}
		}
		return colorMap;
	}

	public static List<CyNode> getResidueNodes(StEMAPManager mgr, CyNetwork net, CyNode node, boolean findMultiples) {
		List<CyNode> residueNodes = new ArrayList<>();
		for (CyNode neighbor: net.getNeighborList(node, CyEdge.Type.ANY)) {
			String pdb = net.getRow(neighbor).get(ModelUtils.RESIDUE_COLUMN, String.class);
			String mutType = net.getRow(neighbor).get(ModelUtils.MUT_TYPE_COLUMN, String.class);
			boolean multiple = false;
			if (mutType != null && (mutType.equals("del") || mutType.equals("multiple")))
				multiple = true;

			if (pdb != null && pdb.length() > 0 && (!multiple || !mgr.ignoreMultiples())) {
				residueNodes.add(neighbor);
			} else if (findMultiples && multiple) {
				List<CyNode> rn = getResidueNodes(mgr, net, neighbor, true);
				if (rn != null) residueNodes.addAll(rn);
			} else if (mutType != null && (!multiple || !mgr.ignoreMultiples())) {
				residueNodes.add(neighbor);
			}
		}
		return residueNodes;
	}

	public static List<String> getResidues(StEMAPManager mgr, CyNetwork net, List<CyNode> nodes) {
		Set<String> resSet = new HashSet<>();
		for (CyNode node: nodes) {
			List<String> residues = getResidue(mgr, net, node);
			if (residues != null && residues.size() > 0)
				resSet.addAll(residues);
		}
		return new ArrayList<String>(resSet);
	}

	public static List<String> getResidue(StEMAPManager manager, CyNetwork net, CyNode node) {
		String pdb = net.getRow(node).get(ModelUtils.RESIDUE_COLUMN, String.class);
		if (pdb == null || pdb.length() == 0)
			return null;
		String[] model = pdb.split("#");
		return addChains(manager, model[1]);
	}

	// FIXME: need to handle multiple residues here!!!!!
	private static List<String> addChains(StEMAPManager manager, String resChain) {
		List<String> residues = new ArrayList<>();
		String [] rc = resChain.split("[.]");
		String chain = rc[1];
		String residue = rc[0];
		// System.out.println("Looking for duplicate chain for '"+chain+"'");
		List<String> chains = manager.getDuplicateChains(chain); // Get the chain aliases
		// System.out.println("Duplicate chains returns: "+chains);
		// System.out.println("Got "+chains.size()+" chains: ");
		if (chains == null) {
			chains = new ArrayList<String>();
		}
		chains.add(chain);
		for (String ch: chains) {
			addResidues(manager, residues, ch, residue);
		}
		// System.out.println("addChains returning: "+residues);
		return residues;
	}

	private static void addResidues(StEMAPManager manager, List<String> residues, String chain, String resSpec) {
		if (resSpec.indexOf("-") > 0) {
			String[] resRange = resSpec.split("-");
			int start = Integer.valueOf(resRange[0]);
			int end = Integer.valueOf(resRange[1]);
			for (int i = start; i <= end; i++) {
				residues.add(i+"."+chain);
			}
		} else if (resSpec.indexOf(",") > 0) {
			String[] resArray = resSpec.split(",");
			for (String res: resArray) {
				residues.add(res+"."+chain);
			}
		} else {
			residues.add(resSpec+"."+chain);
		}
	}

	private static void updateRanges(double weight, Color color, Color[] colorRange, double[] valueRange) {
		// System.out.println("updateRanges: "+weight+", "+color);
		// System.out.println("valueRange: "+valueRange[0]+"-"+valueRange[1]+", "+valueRange[2]+"-"+valueRange[3]);
		// System.out.println("colorRange: "+colorRange[0]+"-"+colorRange[1]+", "+colorRange[2]+"-"+colorRange[3]);
		if (weight < 0.0) {
			if (weight < valueRange[0]) {
				valueRange[0] = weight;
				colorRange[0] = color;
				// System.out.println("new low negative color: "+color);
			} 
			
			if (weight > valueRange[1]) {
				valueRange[1] = weight;
				colorRange[1] = color;
				// System.out.println("new high negative color: "+color);
			}
		} else {
			if (weight < valueRange[2]) {
				valueRange[2] = weight;
				colorRange[2] = color;
				// System.out.println("new low positive color: "+color);
			} 
			
			if (weight > valueRange[3]) {
				valueRange[3] = weight;
				colorRange[3] = color;
				// System.out.println("new high positive color: "+color);
			}
		}
	}
}