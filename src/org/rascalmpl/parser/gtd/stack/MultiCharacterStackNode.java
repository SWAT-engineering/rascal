/*******************************************************************************
 * Copyright (c) 2009-2011 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:

 *   * Arnold Lankamp - Arnold.Lankamp@cwi.nl
*******************************************************************************/
package org.rascalmpl.parser.gtd.stack;

import org.eclipse.imp.pdb.facts.IConstructor;
import org.rascalmpl.parser.gtd.result.AbstractNode;
import org.rascalmpl.parser.gtd.result.LiteralNode;
import org.rascalmpl.parser.gtd.stack.filter.ICompletionFilter;
import org.rascalmpl.parser.gtd.stack.filter.IEnterFilter;

public class MultiCharacterStackNode extends AbstractStackNode implements IMatchableStackNode{
	private final IConstructor production;
	
	private final char[][] characters;
	
	private final AbstractNode result;
	
	public MultiCharacterStackNode(int id, int dot, IConstructor production, char[][] characters){
		super(id, dot);
		
		this.production = production;
		
		this.characters = characters;
		
		result = null;
	}
	
	public MultiCharacterStackNode(int id, int dot, IConstructor production, char[][] characters, IEnterFilter[] enterFilters, ICompletionFilter[] completionFilters){
		super(id, dot, enterFilters, completionFilters);
		
		this.production = production;
		
		this.characters = characters;
		
		result = null;
	}
	
	private MultiCharacterStackNode(MultiCharacterStackNode original, int startLocation){
		super(original, startLocation);
		
		production = original.production;
		
		characters = original.characters;
		
		result = null;
	}
	
	private MultiCharacterStackNode(MultiCharacterStackNode original, int startLocation, AbstractNode result){
		super(original, startLocation);
		
		this.production = original.production;
		
		this.characters = original.characters;
		
		this.result = result;
	}
	
	public boolean isEmptyLeafNode(){
		return false;
	}
	
	public String getName(){
		throw new UnsupportedOperationException();
	}
	
	public AbstractNode match(char[] input, int location){
		int nrOfCharacters = characters.length;
		char[] resultArray = new char[nrOfCharacters];
		
		OUTER : for(int i = nrOfCharacters - 1; i >= 0; --i){
			char next = input[location + i];
			
			char[] alternatives = characters[i];
			for(int j = alternatives.length - 1; j >= 0; --j){
				char alternative = alternatives[j];
				if(next == alternative){
					resultArray[i] = alternative;
					continue OUTER;
				}
			}
			return null;
		}
		
		return new LiteralNode(production, resultArray);
	}
	
	public boolean matchWithoutResult(char[] input, int location){
		int nrOfCharacters = characters.length;
		OUTER : for(int i = nrOfCharacters - 1; i >= 0; --i){
			char next = input[location + i];
			
			char[] alternatives = characters[i];
			for(int j = alternatives.length - 1; j >= 0; --j){
				if(next == alternatives[j]){
					continue OUTER;
				}
			}
			return false;
		}
		
		return true;
	}
	
	public AbstractStackNode getCleanCopy(int startLocation){
		return new MultiCharacterStackNode(this, startLocation);
	}
	
	public AbstractStackNode getCleanCopyWithResult(int startLocation, AbstractNode result){
		return new MultiCharacterStackNode(this, startLocation, result);
	}
	
	public int getLength(){
		return 1;
	}
	
	public AbstractStackNode[] getChildren(){
		throw new UnsupportedOperationException();
	}
	
	public boolean canBeEmpty(){
		throw new UnsupportedOperationException();
	}
	
	public AbstractStackNode getEmptyChild(){
		throw new UnsupportedOperationException();
	}
	
	public AbstractNode getResult(){
		return result;
	}
	
	public String toString(){
		StringBuilder sb = new StringBuilder();
		
		sb.append('[');
		for(int i = characters.length - 1; i >= 0; --i){
			char[] range = characters[i];
			sb.append(range[0]);
			sb.append('-');
			sb.append(range[1]);
		}
		sb.append(']');
		
		sb.append(getId());
		sb.append('(');
		sb.append(startLocation);
		sb.append(')');
		
		return sb.toString();
	}
	
	public boolean isEqual(AbstractStackNode stackNode){
		if(!(stackNode instanceof MultiCharacterStackNode)) return false;
		
		MultiCharacterStackNode otherNode = (MultiCharacterStackNode) stackNode;
		
		char[][] otherCharacters = otherNode.characters;
		if(characters.length != otherCharacters.length) return false;
		
		for(int i = characters.length - 1; i >= 0; --i){
			char[] chars = characters[i];
			char[] otherChars = otherCharacters[i];
			if(chars.length != otherChars.length) return false;
			
			POS: for(int j = chars.length - 1; j <= 0; --j){
				char c = chars[j];
				for(int k = otherChars.length - 1; k <= 0; --k){
					if(c == otherChars[k]) continue POS;
				}
				return false;
			}
		}
		// Found all characters.
		
		return hasEqualFilters(stackNode);
	}
}
