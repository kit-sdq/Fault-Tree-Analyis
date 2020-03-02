package de.fzi.fta.tests;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.junit.Test;

import static de.fzi.fta.faultTree.GateType.AND;
import static de.fzi.fta.faultTree.GateType.OR;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.fzi.fta.analysis.FaultTreeAnalysis;
import de.fzi.fta.analysis.impl.FaultTreeAnalyser;
import de.fzi.fta.cutSet.Cut;
import de.fzi.fta.cutSet.CutSet;
import de.fzi.fta.faultTree.Event;
import de.fzi.fta.faultTree.FaultTree;
import de.fzi.fta.faultTree.FaultTreeFactory;
import de.fzi.fta.faultTree.Gate;
import de.fzi.fta.faultTree.GateType;
import de.fzi.fta.faultTree.IntermediateEvent;
import de.fzi.fta.faultTree.PrimaryEvent;
import de.fzi.fta.faultTree.Transfer;
import de.fzi.fta.faultTree.TransferIn;
import de.fzi.fta.faultTree.TransferOut;
import junit.framework.TestCase;


public class FaultTreeTest extends TestCase {

	private FaultTreeFactory factory = FaultTreeFactory.eINSTANCE;
	
	@Test
	public void testSmallANDFaultTree() throws IOException {
		FaultTree ft = createSmallFaultTree(AND);
		assertTrue(Math.abs(ft.getTopEvent().getProbability() - 0.75 * 0.25) < 0.0000001);
		assertTrue(Math.abs(ft.getGates().get(0).getProbability() - 0.75 * 0.25) < 0.0000001);
		
		FaultTreeAnalysis analyser = new FaultTreeAnalyser(Collections.singletonList(ft));
		CutSet cs = analyser.calculateMinimalCutSet("tree", "topEvent");
		//CS = {{p1,p2}} with P(p1)=0.75 , P(p2)=0.25
		//P(top) = p1*p2
		assertEquals(cs.getCuts().size(), 1);
		Cut cut = cs.getCuts().get(0);
		assertTrue(cut.getEvents().stream().filter(e -> e.getName().equals("p1")).findFirst().isPresent());
		assertTrue(cut.getEvents().stream().filter(e -> e.getName().equals("p2")).findFirst().isPresent());
		assertTrue(Math.abs(cs.calculateProbabilityConservatively() - 0.75 * 0.25) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(new BasicEList<String>(Collections.singletonList("p1")), null) - 0.25) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(new BasicEList<String>(Collections.singletonList("p2")), null) - 0.75) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(null, new BasicEList<String>(Collections.singletonList("p1")))) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(null, new BasicEList<String>(Collections.singletonList("p2")))) < 0.0000001);
		EList<String> list = new BasicEList<String>();
		list.add("p1");
		list.add("p2");
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(null, list)) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(list, null) - 1) < 0.0000001);
	}
	
	@Test
	public void testSmallORFaultTree() throws IOException {
		FaultTree ft = createSmallFaultTree(OR);
		assertTrue(Math.abs(ft.getTopEvent().getProbability() - (1 - 0.25 * 0.75)) < 0.0000001);
		assertTrue(Math.abs(ft.getGates().get(0).getProbability() - (1 - 0.25 * 0.75)) < 0.0000001);
		
		FaultTreeAnalysis analyser = new FaultTreeAnalyser(Collections.singletonList(ft));
		CutSet cs = analyser.calculateMinimalCutSet("tree", "topEvent");
		//CS = {{p1},{p2}} with P(p1)=0.75 , P(p2)=0.25
		//P(top) = (1 - p1)*(1 - p2)
		assertEquals(cs.getCuts().size(), 2);
		Cut cut = cs.getCuts().get(0);
		Cut cut2 = cs.getCuts().get(1);
		assertTrue(cut.getEvents().get(0).getName().equals("p1") || cut.getEvents().get(0).getName().equals("p2"));
		assertTrue(cut2.getEvents().get(0).getName().equals("p1") || cut.getEvents().get(0).getName().equals("p2"));
		assertTrue(Math.abs(cs.calculateProbabilityConservatively() - (1 - 0.25 * 0.75)) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(new BasicEList<String>(Collections.singletonList("p1")), null) - 1) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(new BasicEList<String>(Collections.singletonList("p2")), null) - 1) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(null, new BasicEList<String>(Collections.singletonList("p1"))) - 0.25) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(null, new BasicEList<String>(Collections.singletonList("p2")))- 0.75) < 0.0000001);
		EList<String> list = new BasicEList<String>();
		list.add("p1");
		list.add("p2");
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(null, list)) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(list, null) - 1) < 0.0000001);
		
		

	}
	
	@Test
	public void testFaultTreesWithTransferGatesThatHasOneCut() throws IOException {
		FaultTree topFT = createFaultTree("tree");
		IntermediateEvent i1 = createIntermediateEvent(topFT, "topEvent");
		IntermediateEvent i2 = createIntermediateEvent(topFT, "bottomLeft");
		IntermediateEvent i3 = createIntermediateEvent(topFT, "bottomRight");
		createGate(topFT, AND, i1, i2, i3);
		createTransferGate(topFT, "left", i2, true);
		createTransferGate(topFT, "right", i3, true);
		FaultTree bottomLeft = createSmallFaultTree(AND);
		bottomLeft.setName("left");
		bottomLeft.getTopEvent().setName("bottomLeft");
		createTransferGate(bottomLeft, "left", bottomLeft.getTopEvent(), false);
		FaultTree bottomRight = createSmallFaultTree(OR);
		bottomRight.getTopEvent().setName("bottomRight");
		bottomRight.setName("right");
		for (PrimaryEvent p : bottomRight.getPrimaryEvents()) {
			if (p.getName().equals("p1")) {
				p.setName("p3");
				p.setProbability(0.5);
			}
		}
		createTransferGate(bottomRight, "right", bottomRight.getTopEvent(), false);
		
		List<FaultTree> fts = new ArrayList<FaultTree>();
		fts.add(topFT);
		fts.add(bottomLeft);
		fts.add(bottomRight);
		FaultTreeAnalysis analyser = new FaultTreeAnalyser(fts);
		CutSet cs = analyser.calculateMinimalCutSet("tree", "topEvent");
		//CS = {{p1,p2}} with P(p1)=0.75 , P(p2)=0.25
		//P(top) = p1*p2
		assertEquals(cs.getCuts().size(), 1);
		Cut cut = cs.getCuts().get(0);
		assertTrue(cut.getEvents().stream().filter(e -> e.getName().equals("p1")).findFirst().isPresent());
		assertTrue(cut.getEvents().stream().filter(e -> e.getName().equals("p2")).findFirst().isPresent());
		assertTrue(Math.abs(cs.calculateProbabilityConservatively() - 0.75 * 0.25) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(new BasicEList<String>(Collections.singletonList("p1")), null) - 0.25) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(new BasicEList<String>(Collections.singletonList("p2")), null) - 0.75) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(null, new BasicEList<String>(Collections.singletonList("p1")))) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(null, new BasicEList<String>(Collections.singletonList("p2")))) < 0.0000001);
		EList<String> list = new BasicEList<String>();
		list.add("p1");
		list.add("p2");
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(null, list)) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(list, null) - 1) < 0.0000001);
	}
	
	@Test
	public void testFaultTreesWithTransferGatesThatHasTwoCut() throws IOException {
		FaultTree topFT = createFaultTree("tree");
		IntermediateEvent i1 = createIntermediateEvent(topFT, "topEvent");
		IntermediateEvent i2 = createIntermediateEvent(topFT, "bottomLeft");
		IntermediateEvent i3 = createIntermediateEvent(topFT, "bottomRight");
		createGate(topFT, AND, i1, i2, i3);
		createTransferGate(topFT, "left", i2, true);
		createTransferGate(topFT, "right", i3, true);
		FaultTree bottomLeft = createSmallFaultTree(OR);
		bottomLeft.setName("left");
		bottomLeft.getTopEvent().setName("bottomLeft");
		createTransferGate(bottomLeft, "left", bottomLeft.getTopEvent(), false);
		FaultTree bottomRight = createSmallFaultTree(OR);
		bottomRight.getTopEvent().setName("bottomRight");
		bottomRight.setName("right");
		for (PrimaryEvent p : bottomRight.getPrimaryEvents()) {
			if (p.getName().equals("p1")) {
				p.setName("p3");
				p.setProbability(0.5);
			}
		}
		createTransferGate(bottomRight, "right", bottomRight.getTopEvent(), false);
		
		List<FaultTree> fts = new ArrayList<FaultTree>();
		fts.add(topFT);
		fts.add(bottomLeft);
		fts.add(bottomRight);
		FaultTreeAnalysis analyser = new FaultTreeAnalyser(fts);
		CutSet cs = analyser.calculateMinimalCutSet("tree", "topEvent");
		assertEquals(cs.getCuts().size(), 2);
		//CS = {{p1,p3},{p2}} with P(p1)=0.75 , P(p2)=0.25 , P(p3)=0.5
		//P(top) = 1 - (1 - p1*p3)*(1 - p2)
		Cut cut = cs.getCuts().get(0);
		Cut cut2 = cs.getCuts().get(1);
		if (cut.getEvents().size() == 1) {
			assertEquals("p2", cut.getEvents().get(0).getName());
			assertTrue(cut2.getEvents().stream().filter(e -> e.getName().equals("p1")).findFirst().isPresent());
			assertTrue(cut2.getEvents().stream().filter(e -> e.getName().equals("p3")).findFirst().isPresent());
		} else if (cut.getEvents().size() == 2) {
			assertEquals("p2", cut2.getEvents().get(0).getName());
			assertTrue(cut.getEvents().stream().filter(e -> e.getName().equals("p1")).findFirst().isPresent());
			assertTrue(cut.getEvents().stream().filter(e -> e.getName().equals("p3")).findFirst().isPresent());
		} else {
			fail();
		}
		assertTrue(Math.abs(cs.calculateProbabilityConservatively() - (1 - (1 - 0.75 * 0.5)*(1 - 0.25))) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(new BasicEList<String>(Collections.singletonList("p1")), null) - (1 - (1 - 0.5)*(1 - 0.25))) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(new BasicEList<String>(Collections.singletonList("p2")), null) - 1) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(new BasicEList<String>(Collections.singletonList("p3")), null) - (1 - (1 - 0.75)*(1 - 0.25))) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(null, new BasicEList<String>(Collections.singletonList("p1")))) - 0.25 < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(null, new BasicEList<String>(Collections.singletonList("p2")))) - 0.75*0.5 < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(null, new BasicEList<String>(Collections.singletonList("p3")))) - 0.25 < 0.0000001);
		EList<String> list = new BasicEList<String>();
		list.add("p1");
		list.add("p2");
		list.add("p3");
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(null, list)) < 0.0000001);
		assertTrue(Math.abs(cs.calculateProbabilityConservatively(list, null) - 1) < 0.0000001);
	}

	private Transfer createTransferGate(FaultTree ft, String name, IntermediateEvent intermediateEvent, boolean in) {
		if (in) {
			TransferIn inGate = factory.createTransferIn();
			inGate.setName(name);
			ft.getTransferIns().add(inGate);
			inGate.setEvent(intermediateEvent);
			return inGate;
		} else {
			TransferOut outGate = factory.createTransferOut();	
			outGate.setName(name);
			ft.setTransferOut(outGate);
			outGate.setEvent(intermediateEvent);
			return outGate;
		}
	}
	
	@Test
	public void testCreateIncorrectFaultTree() throws IOException {
		FaultTree ft = createSmallFaultTree(OR);
		createIntermediateEvent(ft, "top2");
		
		try {
			new FaultTreeAnalyser(Collections.singletonList(ft));
			fail();
		} catch (IllegalArgumentException e) {
			// ft has two topEvents
		}

	}
	
	private FaultTree createSmallFaultTree(GateType type) {
		FaultTree ft = createFaultTree("tree");
		IntermediateEvent i = createIntermediateEvent(ft, "topEvent");
		PrimaryEvent p1 = createPrimaryEvent(ft, "p1", 0.75);
		PrimaryEvent p2 = createPrimaryEvent(ft, "p2", 0.25);
		createGate(ft, type, i, p1, p2);
		return ft;
	}

	private Gate createGate(FaultTree ft, GateType type, IntermediateEvent output, Event... inputs) {
		Gate result = factory.createGate();
		result.setOutput(output);
		result.setType(type);
		for(Event i : inputs) {
			result.getInputs().add(i);
		}
		ft.getGates().add(result);
		return result;
	}

	private PrimaryEvent createPrimaryEvent(FaultTree ft, String name, double probability) {
		PrimaryEvent result = factory.createPrimaryEvent();
		result.setName(name);
		result.setProbability(probability);
		ft.getPrimaryEvents().add(result);
		return result;
	}
	
	private IntermediateEvent createIntermediateEvent(FaultTree ft, String name) {
		IntermediateEvent result = factory.createIntermediateEvent();
		result.setName(name);
		ft.getIntermediateEvents().add(result);
		return result;
	}

	private FaultTree createFaultTree(String name) {
		FaultTree result = factory.createFaultTree();
		result.setName(name);
		return result;
	}

} 
