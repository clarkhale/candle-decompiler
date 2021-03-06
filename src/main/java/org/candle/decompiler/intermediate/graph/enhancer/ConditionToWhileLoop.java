package org.candle.decompiler.intermediate.graph.enhancer;

import java.util.List;
import java.util.TreeSet;

import org.apache.bcel.generic.BranchHandle;
import org.candle.decompiler.intermediate.code.AbstractIntermediate;
import org.candle.decompiler.intermediate.code.BooleanBranchIntermediate;
import org.candle.decompiler.intermediate.code.GoToIntermediate;
import org.candle.decompiler.intermediate.code.IntermediateComparator;
import org.candle.decompiler.intermediate.code.StatementIntermediate;
import org.candle.decompiler.intermediate.code.loop.WhileIntermediate;
import org.candle.decompiler.intermediate.expression.Continue;
import org.candle.decompiler.intermediate.graph.GraphIntermediateVisitor;
import org.candle.decompiler.intermediate.graph.context.IntermediateGraphContext;
import org.candle.decompiler.intermediate.graph.edge.IntermediateEdge;
import org.jgrapht.Graphs;
import org.jgrapht.alg.CycleDetector;

public class ConditionToWhileLoop extends GraphIntermediateVisitor {
	
	public ConditionToWhileLoop(IntermediateGraphContext igc) {
		super(igc, true);
	}

	@Override
	public void visitBooleanBranchIntermediate(BooleanBranchIntermediate line) {
		List<AbstractIntermediate> predecessors = Graphs.predecessorListOf(igc.getGraph(), line);
		
		CycleDetector<AbstractIntermediate, IntermediateEdge> cycleDetector = new CycleDetector<AbstractIntermediate, IntermediateEdge>(igc.getGraph());
		if(!cycleDetector.detectCyclesContainingVertex(line)) {
			return;
		}
		
		//first, determine if the condition has two incoming lines.
		if(predecessors.size() >= 2) {
			//check to see that 1 predecessor is a GOTO.
			
			TreeSet<GoToIntermediate> incomingGotoNonNested = new TreeSet<GoToIntermediate>(new IntermediateComparator());
			TreeSet<GoToIntermediate> incomingGotoNested = new TreeSet<GoToIntermediate>(new IntermediateComparator());
			GoToIntermediate nestedLine = null;
			AbstractIntermediate otherLine = null;
			
			//classify.
			for(AbstractIntermediate predecessor : predecessors) {
				//check to see if 1 is GOTO.
				if(predecessor instanceof GoToIntermediate) {
					if(isNested(line, predecessor)) {
						incomingGotoNested.add((GoToIntermediate)predecessor);
					}
					else {
						incomingGotoNonNested.add((GoToIntermediate)predecessor);
					}
					continue;
				}
				else {
					otherLine = predecessor;
				}
			}
			
			//if there are more than one GOTO statements that are not-nested, return.
			if(incomingGotoNonNested.size() > 1) {
				return;
			}
			
			nestedLine = getCandidateGoto(incomingGotoNonNested, incomingGotoNested);
			
			//stop if both conditions aren't met.
			if(nestedLine == null || otherLine == null) {
				return;
			}
			
			//check to validate that the GOTO instruction is less than the other incoming...
			if(comparator.before(otherLine, line)) {
				//take the lower condition...
				BranchHandle refHandle = null;
				if(comparator.before(nestedLine, line)) {
					refHandle = (BranchHandle)nestedLine.getInstruction();
				}
				else {
					refHandle = (BranchHandle)line.getInstruction();
				}
				
				
				WhileIntermediate whileIntermediate = new WhileIntermediate(refHandle, line);
				
				//add this to the graph.
				this.igc.getGraph().addVertex(whileIntermediate);
				
				//get the incoming from the goto...
				igc.redirectPredecessors(nestedLine, whileIntermediate);
				igc.redirectSuccessors(line, whileIntermediate);
								
				//now, create line from other to while.
				this.igc.getGraph().addEdge(otherLine, whileIntermediate);
				
				//now, remove the GOTO and Conditional Vertex from graph.
				igc.getGraph().removeVertex(nestedLine);
				igc.getGraph().removeVertex(line);
				
				
				
				//now, the other GOTO lines coming in should all be CONTINUE statements...
				for(GoToIntermediate gotoIntermediate : incomingGotoNested) {
					Continue continueExpression = new Continue(gotoIntermediate.getInstruction()); 
					StatementIntermediate continueIntermediate = new StatementIntermediate(gotoIntermediate.getInstruction(), continueExpression);

					//add the node...
					igc.getGraph().addVertex(continueIntermediate);
					igc.redirectPredecessors(gotoIntermediate, continueIntermediate);
					//remove vertex.
					igc.getGraph().removeVertex(gotoIntermediate);
					
					//now, add line to the loop.
					igc.getGraph().addEdge(continueIntermediate, whileIntermediate);
				}
				
				updateEdges(whileIntermediate);
			}
		}
	}
	
	protected GoToIntermediate getCandidateGoto(TreeSet<GoToIntermediate> incomingGotoNonNested, TreeSet<GoToIntermediate> incomingGotoNested) {
		return incomingGotoNonNested.pollLast();
	}
	
	protected void updateEdges(WhileIntermediate wi) {
		List<AbstractIntermediate> predecessors = igc.getPredecessors(wi);
		for(AbstractIntermediate predecessor : predecessors) {
			IntermediateEdge ie = igc.getGraph().getEdge(predecessor, wi);
			igc.validateBackEdge(ie);
		}
		List<AbstractIntermediate> successors = igc.getSuccessors(wi);
		for(AbstractIntermediate successor : successors) {
			IntermediateEdge ie = igc.getGraph().getEdge(wi, successor);
			igc.validateBackEdge(ie);
		}
	}

	protected boolean isNested(BooleanBranchIntermediate ci, AbstractIntermediate ai) 
	{
		int max = igc.getFalseTarget(ci).getInstruction().getPosition();
		int min = igc.getTrueTarget(ci).getInstruction().getPosition();
		
		return (ai.getInstruction().getPosition() <= max && ai.getInstruction().getPosition() >= min);
		
		
	}
	
}
