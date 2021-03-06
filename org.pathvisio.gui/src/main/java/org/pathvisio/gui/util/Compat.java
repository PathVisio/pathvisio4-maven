/*******************************************************************************
 * PathVisio, a tool for data visualization and analysis using biological pathways
 * Copyright 2006-2021 BiGCaT Bioinformatics, WikiPathways
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package org.pathvisio.gui.util;

import java.util.HashMap;
import java.util.Map;

import javax.swing.JOptionPane;

import org.bridgedb.DataSource;
import org.bridgedb.Xref;
import org.bridgedb.bio.Organism;
import org.pathvisio.core.ApplicationEvent;
import org.pathvisio.core.Engine;
import org.pathvisio.model.PathwayModel;
import org.pathvisio.model.PathwayObject;
import org.pathvisio.util.XrefUtils;
import org.pathvisio.model.DataNode;
import org.pathvisio.model.PathwayElement;
import org.pathvisio.gui.SwingEngine;

/**
 * File and schema version compatibilities checks This collects some methods
 * that are perhaps too hacky to run in the wild.
 */
public class Compat implements Engine.ApplicationEventListener {
	private final SwingEngine swingEngine;

	private Map<Organism, DataSource> ensSpecies = new HashMap<Organism, DataSource>();

	public Compat(SwingEngine swingEngine) {
		this.swingEngine = swingEngine;

		ensSpecies.put(Organism.HomoSapiens, DataSource.getByCompactIdentifierPrefix("ensembl")); // BioDataSource.ENSEMBL_HUMAN);
		ensSpecies.put(Organism.CaenorhabditisElegans, DataSource.getByCompactIdentifierPrefix("ensembl")); // BioDataSource.ENSEMBL_CELEGANS);
		ensSpecies.put(Organism.DanioRerio, DataSource.getByCompactIdentifierPrefix("ensembl"));// BioDataSource.ENSEMBL_ZEBRAFISH);
		ensSpecies.put(Organism.DrosophilaMelanogaster, DataSource.getByCompactIdentifierPrefix("ensembl"));// BioDataSource.ENSEMBL_ZEBRAFISH);
		ensSpecies.put(Organism.MusMusculus, DataSource.getByCompactIdentifierPrefix("ensembl"));// BioDataSource.ENSEMBL_MOUSE);
		ensSpecies.put(Organism.RattusNorvegicus, DataSource.getByCompactIdentifierPrefix("ensembl"));// BioDataSource.ENSEMBL_RAT);
		ensSpecies.put(Organism.SaccharomycesCerevisiae, DataSource.getByCompactIdentifierPrefix("ensembl"));// BioDataSource.ENSEMBL_SCEREVISIAE);
	}

	private boolean usesOldEnsembl(PathwayModel pwy) {
		Organism org = Organism.fromLatinName(pwy.getPathway().getOrganism());
		if (!ensSpecies.containsKey(org))
			return false; // this pwy is not one of the species to be converted

		for (PathwayObject elt : pwy.getPathwayObjects()) {
			if (elt.getClass() == DataNode.class) {
				if (((DataNode) elt).getXref().getDataSource() == DataSource.getByCompactIdentifierPrefix("ensembl")) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Ensembl considers each species database as separate, and thus they should
	 * have separate system codes as well. This method will convert generic Ensembl
	 * datanodes to species specific datanodes if possible.
	 */
	private void convertEnsembl(PathwayModel pwy) {
		Organism org = Organism.fromLatinName(pwy.getPathway().getOrganism());
		if (!ensSpecies.containsKey(org))
			return; // this pwy is not one of the species to be converted

		for (PathwayObject elt : pwy.getPathwayObjects()) {
			if (elt.getClass() == DataNode.class) {
				if (((DataNode) elt).getXref().getDataSource() == DataSource.getByCompactIdentifierPrefix("ensembl")) {
					((DataNode) elt).setXref(new Xref(((DataNode) elt).getXref().getId(), ensSpecies.get(org)));
				}
			}
		}

	}

	public void applicationEvent(ApplicationEvent e) {
		switch (e.getType()) {
		case PATHWAY_OPENED: {
			PathwayModel pwy = swingEngine.getEngine().getActivePathwayModel();
			if (usesOldEnsembl(pwy)) {
				int result = JOptionPane.showConfirmDialog(swingEngine.getFrame(),
						"This Pathway uses the old style references to Ensembl.\nDo you want"
								+ "to update this pathway?\n\n"
								+ "This update is required if you want to use the latest gene databases.",
						"Update pathway?", JOptionPane.YES_NO_OPTION);
				if (result == JOptionPane.YES_OPTION) {
					convertEnsembl(pwy);
				}
			}
		}
		}

	}

}
