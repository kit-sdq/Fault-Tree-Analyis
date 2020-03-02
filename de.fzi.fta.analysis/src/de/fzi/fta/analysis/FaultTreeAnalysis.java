package de.fzi.fta.analysis;

import java.io.IOException;

import de.fzi.fta.cutSet.CutSet;

public interface FaultTreeAnalysis {
	
	CutSet calculateMinimalCutSet(String faultTreeName, String topEventName) throws IOException;

	
}
