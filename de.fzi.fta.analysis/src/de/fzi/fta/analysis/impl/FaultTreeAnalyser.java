package de.fzi.fta.analysis.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;

import com.google.common.collect.Sets;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;

import de.fzi.fta.analysis.FaultTreeAnalysis;
import de.fzi.fta.cutSet.Cut;
import de.fzi.fta.cutSet.CutSet;
import de.fzi.fta.cutSet.CutSetFactory;
import de.fzi.fta.cutSet.Event;
import de.fzi.fta.faultTree.FaultTree;
import de.fzi.fta.faultTree.Gate;
import de.fzi.fta.faultTree.IntermediateEvent;
import de.fzi.fta.faultTree.PrimaryEvent;
import de.fzi.fta.faultTree.TransferIn;
import de.fzi.fta.faultTree.TransferOut;

public class FaultTreeAnalyser implements FaultTreeAnalysis {

	private final String FILE_EXTENSION = ".faulttree";
	private ResourceSet faultTrees;
	private Map<String, String> transferOutOrigin;
	private List<PrimaryEvent> relevantPrimaryEvents;
	public FaultTreeAnalyser(Collection<FaultTree> faultTrees) throws IOException {
		if (faultTrees == null || faultTrees.isEmpty()) {
			throw new IllegalArgumentException("The parameter faultTrees needs at least one fault tree.");
		}
		this.transferOutOrigin  = new HashMap<String, String>();
		this.faultTrees = new ResourceSetImpl();
		MutableGraph<String> graph = GraphBuilder.directed().build();
		for (FaultTree tree : faultTrees) {
			String treeName = tree.getName();
			Resource r = this.faultTrees.createResource(getFaultTreeURI(treeName));
			r.getContents().add(tree);
			TransferOut transferOut = tree.getTransferOut();
			if (transferOut != null) {
				if (transferOutOrigin.put(transferOut.getName(), treeName) != null) {
					throw new IllegalArgumentException(String.format("TransferOut %s has more than one origin!", transferOut.getName()));
				}
			}
			graph.addNode(treeName);
			
			Diagnostic diagnostic = Diagnostician.INSTANCE.validate(tree);
			if (diagnostic.getSeverity() == Diagnostic.ERROR) {
				throw new IllegalArgumentException(String.format("Error while validating fault tree %s. Error message: ", tree.getName())+ diagnostic.getMessage());
			}
		}
		for (FaultTree tree : faultTrees) {	
			for (TransferIn in : tree.getTransferIns()) {
				String out = transferOutOrigin.get(in.getName());
				if (out == null) {
					throw new IllegalArgumentException("No TransferOut found for TransferIn with the name: \"" + in.getName() + "\"");
				}
				graph.putEdge(tree.getName(), out);
			}
		}
		if (Graphs.hasCycle(graph)) {
			throw new IllegalArgumentException("Cycle detected in fault trees");
		}
	}
	
	
	@Override
	public CutSet calculateMinimalCutSet(String faultTreeName, String topEventName) throws IOException {
		checkParameter(faultTreeName, topEventName);
		relevantPrimaryEvents = new ArrayList<PrimaryEvent>();
		FaultTree faultTree = getFaultTreeWithName(faultTreeName);
		IntermediateEvent topEvent = faultTree.getTopEvent();
		if (topEvent == null || topEvent.getName().equals(topEventName) == false) {
			topEvent = (IntermediateEvent) faultTree.getEvents().parallelStream().filter(e -> e.getName().equals(topEventName)).filter(e-> e instanceof IntermediateEvent).findFirst().get();
		}
		Set<Set<String>> mcs = performMOCUS(topEvent);
		return transformToModel(mcs);
	}


	private CutSet transformToModel(Set<Set<String>> mcs) {
		checkForSubSets(mcs);
		Map<String, PrimaryEvent> searchMap = new HashMap<String, PrimaryEvent>();
		for (PrimaryEvent e : relevantPrimaryEvents) {
			searchMap.put(e.getName(), e);
		}
		
		CutSetFactory factory = CutSetFactory.eINSTANCE;
		CutSet result = factory.createCutSet();
		result.setIsMinimal(true);
		for (Set<String> set : mcs) {
			Cut cut = factory.createCut();
			for (String s : set) {
				Event e = factory.createEvent();
				e.setName(s);
				e.setProbability(searchMap.get(s).getProbability());
				cut.getEvents().add(e);
			}
			result.getCuts().add(cut);
		}
		return result;
	}


	private Set<Set<String>> performMOCUS(IntermediateEvent topEvent) {
		Set<Set<String>> mcs = new HashSet<Set<String>>();
		Set<String> set = new HashSet<String>();
		set.add(topEvent.getName());
		mcs.add(set);
		return performMOCUS(topEvent, mcs);
	}
	
	private Set<Set<String>> performMOCUS(IntermediateEvent topEvent, Set<Set<String>> mcs) {
		relevantPrimaryEvents.addAll(topEvent.getFaultTree().getPrimaryEvents());
		List<IntermediateEvent> intermediateEvents = new ArrayList<IntermediateEvent>();
		intermediateEvents.add(topEvent);
		while(intermediateEvents.size() > 0) {
			IntermediateEvent event = intermediateEvents.remove(0);
			if (event.getBottomEvent() != null) {
				intermediateEvents.add(event.getBottomEvent());
				replaceEventWithEvents(mcs, event.getName(), Collections.singletonList(event.getBottomEvent().getName()));
			} else if(event.getBottomGate() != null){
				resolveGateForEvent(mcs, event);
				intermediateEvents.addAll(Arrays.asList(event.getBottomGate().getInputs().parallelStream().filter(e-> e instanceof IntermediateEvent).toArray(IntermediateEvent[]::new)));
			} else /*(event.getTransferIn() != null)*/{
				String subTreeName = this.transferOutOrigin.get(event.getTransferIn().getName());
				if (subTreeName == null || subTreeName.length() == 0) {
					throw new IllegalStateException();
				}
				FaultTree subTree = getFaultTreeWithName(subTreeName);
				if (subTree.getTopEvent().getName().equals(event.getName()) == false) {
					throw new IllegalStateException();
				}
				performMOCUS(subTree.getTopEvent(), mcs);
			}
		}

		return mcs;
	}

	private void checkForSubSets(Set<Set<String>> mcs) {
		List<Set<String>> setsToRemove = new ArrayList<Set<String>>();
		for (Set<String> s1 : mcs) {
			for (Set<String> s2 : mcs) {
				if (isSubset(s1, s2)) {
					setsToRemove.add(s2);
				}
			}
		}
		for (Set<String> s1 : setsToRemove) {
			mcs.removeIf(s2 -> s2.equals(s1));
		}
	}


	private boolean isSubset(Set<String> subset, Set<String> superset) {
		return Sets.difference(subset, superset).isEmpty() && Sets.difference(superset, subset).isEmpty() == false;
	}


	private void resolveGateForEvent(Set<Set<String>> mcs, IntermediateEvent topEvent) {
		Gate gate = topEvent.getBottomGate();
		List<String> inputEvents = new ArrayList<String>();
		gate.getInputs().stream().forEachOrdered(e -> inputEvents.add(e.getName()));
		switch(gate.getType()) {
		case AND:
			replaceEventWithEvents(mcs, topEvent.getName(), inputEvents);
			break;
		case PAND:
			replaceEventWithEvents(mcs, topEvent.getName(), inputEvents);
			break;
		case OR :
			resolveOrGate(mcs, topEvent.getName(), inputEvents);
			break;
		case XOR:
			resolveOrGate(mcs, topEvent.getName(), inputEvents);
			break;
		default:
			throw new IllegalStateException("Fault tree uses not yet supported GateType " + gate.getType());
		}
	}


	private void resolveOrGate(Set<Set<String>> mcs, String eventToReplace, List<String> inputEvents) {
		List<Set<String>> setsToAdd = new ArrayList<Set<String>>();
		for (Set<String> cut : mcs) {
			if (cut.remove(eventToReplace)) {
				for (int i = 1; i < inputEvents.size(); ++i) {
					Set<String> newCut = cloneSet(cut);
					newCut.add(inputEvents.get(i));
					setsToAdd.add(newCut);
				}
				cut.add(inputEvents.get(0));
			}
		}
		mcs.addAll(setsToAdd);
	}


	private Set<String> cloneSet(Set<String> set) {
		Set<String> result = new HashSet<String>();
		for (String s : set) {
			result.add(new String(s));
		}
		return result;
	}


	private void replaceEventWithEvents(Set<Set<String>> mcs, String eventToReplace, List<String> newEvents) {
		for (Set<String> cut : mcs) {
			if (cut.remove(eventToReplace)) {
				cut.addAll(newEvents);
			}
		}
		
	}


	private void checkParameter(final String faultTreeName, final String topEventName) {
		if (topEventName == null || topEventName.isEmpty()) {
			throw new IllegalArgumentException("null or empty string are invalid values for the parameter \"topEvent\"");
		}
		if (faultTreeName == null || topEventName.isEmpty()) {
			throw new IllegalArgumentException("null or empty string are invalid values for the parameter \"faultTreeName\"");
		}
		FaultTree faultTree = getFaultTreeWithName(faultTreeName);
		if (faultTree == null) {
			throw new IllegalArgumentException(String.format("Fault Tree %s could not be found.", faultTreeName));
		}
		faultTree.getEvents().parallelStream().filter(e -> e.getName().equals(topEventName)).filter(e-> e instanceof IntermediateEvent).findFirst()
			.orElseThrow(()-> new IllegalArgumentException(String.format("Intermediate event %s could not be found in fault tree %s.", topEventName, faultTreeName)));
	}


	private URI getFaultTreeURI(final String faultTreeName) {
		return URI.createURI("fta" + File.separator + faultTreeName + FILE_EXTENSION, true);
	}

	private FaultTree getFaultTreeWithName(String faultTreeName) {
		return (FaultTree) this.faultTrees.getResource(getFaultTreeURI(faultTreeName), true).getContents().get(0);
	}

}
