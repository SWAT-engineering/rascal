/*******************************************************************************
 * Copyright (c) 2009-2011 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:

 *   * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI
 *   * Tijs van der Storm - Tijs.van.der.Storm@cwi.nl
 *   * Emilie Balland - (CWI)
 *   * Paul Klint - Paul.Klint@cwi.nl - CWI
 *   * Mark Hills - Mark.Hills@cwi.nl (CWI)
 *   * Arnold Lankamp - Arnold.Lankamp@cwi.nl
*******************************************************************************/
package org.rascalmpl.interpreter.env;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.eclipse.imp.pdb.facts.IConstructor;
import org.eclipse.imp.pdb.facts.ISet;
import org.eclipse.imp.pdb.facts.ISourceLocation;
import org.eclipse.imp.pdb.facts.IValue;
import org.eclipse.imp.pdb.facts.exceptions.FactTypeUseException;
import org.eclipse.imp.pdb.facts.type.Type;
import org.eclipse.imp.pdb.facts.type.TypeStore;
import org.rascalmpl.ast.AbstractAST;
import org.rascalmpl.ast.Name;
import org.rascalmpl.ast.QualifiedName;
import org.rascalmpl.ast.Import.Syntax;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.asserts.ImplementationError;
import org.rascalmpl.interpreter.result.AbstractFunction;
import org.rascalmpl.interpreter.result.ConstructorFunction;
import org.rascalmpl.interpreter.result.OverloadedFunctionResult;
import org.rascalmpl.interpreter.result.Result;
import org.rascalmpl.interpreter.result.ResultFactory;
import org.rascalmpl.interpreter.utils.Names;

/**
 * A simple environment for variables and functions and types.
 * TODO: this class does not support shadowing of variables and functions yet, which is wrong.
 */
public class Environment {
	
	// TODO: these NameFlags should also be used to implement Public & Private ??
	protected static class NameFlags {
		public final static int FINAL_NAME = 0x01;
		public final static int OVERLOADABLE_NAME = 0x02;
		private int flags = 0;

		public NameFlags(int flags) {
			this.flags = flags;
		}
		
		public int getFlags() {
			return flags;
		}
		
		public void setFlags(int flags) {
			this.flags = flags;
		}
	}
	
	protected Map<String, Result<IValue>> variableEnvironment;
	protected Map<String, OverloadedFunctionResult> functionEnvironment;
	protected Map<String,NameFlags> nameFlags;
	protected Map<Type, Type> typeParameters;
	protected final Environment parent;
	protected final Environment callerScope;
	protected ISourceLocation callerLocation; // different from the scope location (more precise)
	protected final ISourceLocation loc;
	protected final String name;

	public Environment(String name) {
		this(null, null, null, null, name);
	}

	public Environment(Environment parent, String name) {
		this(parent, null, null, null, name);
	}

	public Environment(Environment parent, ISourceLocation loc, String name) {
		this(parent, null, null, loc, name);
	}

	public Environment(Environment parent, Environment callerScope, ISourceLocation callerLocation, ISourceLocation loc, String name) {
		this.parent = parent;
		this.loc = loc;
		this.name = name;
		this.callerScope = callerScope;
		this.callerLocation = callerLocation;
		if (parent == this) {
			throw new ImplementationError("internal error: cyclic environment");
		}
	}

	/**
	 * @return the name of this environment/stack frame for use in tracing
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the location where this environment was created (i.e. call site) for use in tracing
	 */
	public ISourceLocation getLocation() {
		return loc;
	}

	public boolean isRootScope() {
		if (!(this instanceof ModuleEnvironment)) {
			return false;
		}
		return parent == null;
	}

	public boolean isRootStackFrame() {
		return getCallerScope() == null;
	}

	/**
	 * Look up a variable, traversing the parents of the current scope
	 * until it is found.
	 * @param name
	 * @return
	 */
	public Result<IValue> getVariable(QualifiedName name) {
		if (name.getNames().size() > 1) {
			Environment current = this;
			while (!current.isRootScope()) {
				current = current.parent;
			}

			return current.getVariable(name);
		}

		String varName = Names.name(Names.lastName(name));
		return getVariable(varName);
	}

	public Result<IValue> getVariable(Name name) {
		return getVariable(Names.name(name));
	}
	
	public void storeVariable(QualifiedName name, Result<IValue> result) {
		if (name.getNames().size() > 1) {
			if (!isRootScope()) {
				parent.storeVariable(name, result);
			}
		}
		else {
			String varName = Names.name(Names.lastName(name));
			storeVariable(varName, result);
		}
	}

	public Result<IValue> getVariable(String name) {
		Result<IValue> t = getSimpleVariable(name);
		
		if (t != null) {
			return t;
		}
		
		return getAllFunctions(name);
	}
	
	protected Result<IValue> getSimpleVariable(String name) {
		Result<IValue> t = null;
		
		if (variableEnvironment != null) {
			t = variableEnvironment.get(name);
		}
		
		if (t != null) {
			return t;
		}
		
		if (!isRootScope()) {
			return parent.getSimpleVariable(name);
		}
		
		return null;
	}
	
	protected OverloadedFunctionResult getAllFunctions(String name) {
		OverloadedFunctionResult result = null;
		
		if (functionEnvironment != null) {
			result = functionEnvironment.get(name);
		}
		
		if (isRootScope()) {
			return result;
		}
		
		if (result == null) {
			return parent.getAllFunctions(name);
		}
		
		OverloadedFunctionResult resultFromParent = parent.getAllFunctions(name);
		if(resultFromParent == null) return result;
		
		return result.join(resultFromParent);
	}

	protected boolean isNameFlagged(QualifiedName name, int flags) {
		if (name.getNames().size() > 1) {
			Environment current = this;
			while (!current.isRootScope()) {
				current = current.parent;
			}
			
			return current.isNameFlagged(name, flags);
		}
		
		String simpleName = Names.name(Names.lastName(name));
		return isNameFlagged(simpleName, flags);
		
	}
	
	protected boolean isNameFlagged(String name, int flags) {
		Environment flaggingEnvironment = getFlagsEnvironment(name);
		if (flaggingEnvironment == null) {
			return false;
		}
		return flaggingEnvironment.nameFlags.containsKey(name) && (0 != (flaggingEnvironment.nameFlags.get(name).getFlags() & flags));
	}

	public boolean isNameFinal(QualifiedName name) {
		return isNameFlagged(name, NameFlags.FINAL_NAME);
	}

	public boolean isNameOverloadable(QualifiedName name) {
		return isNameFlagged(name, NameFlags.OVERLOADABLE_NAME);
	}

	protected void flagName(QualifiedName name, int flags) {
		if (name.getNames().size() > 1) {
			Environment current = this;
			while (!current.isRootScope()) {
				current = current.parent;
			}
			
			current.flagName(name, flags);
		}
		
		String simpleName = Names.name(Names.lastName(name));
		flagName(simpleName, flags);
		
	}
	
	protected void flagName(String name, int flags) {
		// NOTE: This assumption is that the environment level is already correct, i.e.,
		// we are not requested to mark a name that is higher up in the hierarchy.
		if (nameFlags == null) {
			nameFlags = new HashMap<String,NameFlags>();
		}
		if (nameFlags.containsKey(name)) {
			nameFlags.get(name).setFlags(nameFlags.get(name).getFlags() | flags);
		}
		else {
			nameFlags.put(name, new NameFlags(flags));
		}
	}
	
	public void markNameFinal(QualifiedName name) {
		flagName(name, NameFlags.FINAL_NAME);
	}
	
	public void markNameFinal(String name) {
		flagName(name, NameFlags.FINAL_NAME);
	}

	public void markNameOverloadable(QualifiedName name) {
		flagName(name, NameFlags.OVERLOADABLE_NAME);
	}

	public void markNameOverloadable(String name) {
		flagName(name, NameFlags.OVERLOADABLE_NAME);
	}
	
	public void storeParameterType(Type par, Type type) {
		if (typeParameters == null) {
			typeParameters = new HashMap<Type,Type>();
		}
		typeParameters.put(par, type);
	}

	public Type getParameterType(Type par) {
		if (typeParameters != null) {
			return typeParameters.get(par);
		}
		return null;
	}

	/**
	 * Search for the environment that declared a variable, down to the module
	 * scope.
	 */
	private Map<String,Result<IValue>> getVariableDefiningEnvironment(String name) {
		if (variableEnvironment != null) {
			Result<IValue> r = variableEnvironment.get(name);

			if (r != null) {
				return variableEnvironment;
			}
		}

		return isRootScope() ? null : parent.getVariableDefiningEnvironment(name);
	}

	/**
	 * Search for the environment that declared a variable, but stop just above
	 * the module scope such that we end up in a function scope or a shell scope.
	 */
	private Map<String,Result<IValue>> getLocalVariableDefiningEnvironment(String name) {
		if (variableEnvironment != null) {
			Result<IValue> r = variableEnvironment.get(name);

			if (r != null) {
				return variableEnvironment;
			}
		}

		if (parent == null || parent.isRootScope()) {
			return variableEnvironment;
		}

		return parent.getLocalVariableDefiningEnvironment(name);
	}

	/**
	 * Store a variable the scope that it is declared in, down to the 
	 * module scope if needed.
	 */
	public void storeVariable(String name, Result<IValue> value) {
		//System.err.println("storeVariable: " + name + value.getValue());
		Map<String,Result<IValue>> env = getVariableDefiningEnvironment(name);
		
		if (env == null) {
			// an undeclared variable, which gets an inferred type
			if (variableEnvironment == null) {
				variableEnvironment = new HashMap<String,Result<IValue>>();
			}
			variableEnvironment.put(name, value);
			//System.out.println("Inferred: " + name);
			if (value != null) {
				value.setInferredType(true);
			}
		}
		else {
			// a declared variable
			Result<IValue> old = env.get(name);
			value.setPublic(old.isPublic());
			env.put(name, value);
		}
	}

	/**
	 * Store a variable the scope that it is declared in, but not in the
	 * module (root) scope. This should result in storing variables in either
	 * the function scope or the shell scope. If a variable is not declared yet,
	 * this function will store it in the function scope.
	 */
	public void storeLocalVariable(String name, Result<IValue> value) {
		Map<String,Result<IValue>> env = getLocalVariableDefiningEnvironment(name);

		if (env == null) {
			throw new ImplementationError("storeVariable should always find a scope");
		}

		Result<IValue> old = env.get(name);

		if (old == null) {
			value.setInferredType(true);
		}

		env.put(name, value);
	}

	public void storeVariable(Name name, Result<IValue> r) {
		storeVariable(Names.name(name), r);
	}
 
	public void storeFunction(String name, AbstractFunction function) {
		OverloadedFunctionResult list = null;
		
		if (functionEnvironment != null) {
			list = functionEnvironment.get(name);
		}
		
		if (list == null || !this.isNameFlagged(name,NameFlags.OVERLOADABLE_NAME)) {
			list = new OverloadedFunctionResult(function);
			if (functionEnvironment == null) {
				functionEnvironment = new HashMap<String, OverloadedFunctionResult>();
			}
			functionEnvironment.put(name, list);
		}

		functionEnvironment.put(name, list.add(function));
	}

	public boolean declareVariable(Type type, Name name) {
		return declareVariable(type, Names.name(name));
	}

	public boolean declareVariable(Type type, String name) {
		if (variableEnvironment != null) {
			if (variableEnvironment.get(name) != null) {
				return false;
			}
		}
		if (variableEnvironment == null) {
			variableEnvironment = new HashMap<String,Result<IValue>>();
		}
		variableEnvironment.put(name, ResultFactory.nothing(type));
		return true;
	}
	
	public Map<Type, Type> getTypeBindings() {
		Environment env = this;
		Map<Type, Type> result = new HashMap<Type,Type>();

		while (env != null) {
			if (env.typeParameters != null) {
				for (Type key : env.typeParameters.keySet()) {
					if (!result.containsKey(key)) {
						result.put(key, env.typeParameters.get(key));
					}
				}
			}
			env = env.parent;
		}

		return Collections.unmodifiableMap(result);
	}

	public void storeTypeBindings(Map<Type, Type> bindings) {
		if (typeParameters == null) {
			typeParameters = new HashMap<Type, Type>();
		}
		typeParameters.putAll(bindings);
	}

	@Override
	public String toString(){
		StringBuffer res = new StringBuffer();
		if (functionEnvironment != null) {
			for(String name : functionEnvironment.keySet()){
				res.append(name).append(": ").append(functionEnvironment.get(name)).append("\n");
			}
		}
		if (variableEnvironment != null) {
			for(String name : variableEnvironment.keySet()){
				res.append(name).append(": ").append(variableEnvironment.get(name)).append("\n");
			}
		}
		return res.toString();
	}

	public ModuleEnvironment getImport(String moduleName) {
		return getRoot().getImport(moduleName);
	}

	public boolean isTreeConstructorName(QualifiedName name, Type signature) {
		return getRoot().isTreeConstructorName(name, signature);
	}

	public Environment getRoot() {
		if (parent == null) {
			return this;
		}
		
		Environment target = parent;
		while (target!=null && !target.isRootScope()) {
			target = target.parent;
		}
		return target;
	}
	
	public GlobalEnvironment getHeap() {
		return getRoot().getHeap();
	}

	public Type getConstructor(Type sort, String cons, Type args) {
		return getRoot().getConstructor(sort, cons, args);
	}

	public Type getConstructor(String cons, Type args) {
		return getRoot().getConstructor(cons, args);
	}

	public Type getAbstractDataType(String sort) {
		return getRoot().getAbstractDataType(sort);
	}

	public Type lookupAbstractDataType(String name) {
		return getRoot().lookupAbstractDataType(name);
	}

	public Type lookupConcreteSyntaxType(String name) {
		return getRoot().lookupConcreteSyntaxType(name);
	}


	public Type lookupAlias(String name) {
		return getRoot().lookupAlias(name);
	}

	public Set<Type> lookupAlternatives(Type adt) {
		return getRoot().lookupAlternatives(adt);
	}

	public Type lookupConstructor(Type adt, String cons, Type args) {
		return getRoot().lookupConstructor(adt, cons, args);
	}

	public Set<Type> lookupConstructor(Type adt, String constructorName)
	throws FactTypeUseException {
		return getRoot().lookupConstructor(adt, constructorName);
	}

	public Set<Type> lookupConstructors(String constructorName) {
		return getRoot().lookupConstructors(constructorName);
	}

	public Type lookupFirstConstructor(String cons, Type args) {
		return getRoot().lookupFirstConstructor(cons, args);
	}

	public boolean declaresAnnotation(Type type, String label) {
		return getRoot().declaresAnnotation(type, label);
	}

	public Type getAnnotationType(Type type, String label) {
		return getRoot().getAnnotationType(type, label);
	}

	public void declareAnnotation(Type onType, String label, Type valueType) {
		getRoot().declareAnnotation(onType, label, valueType);
	}

	public Type abstractDataType(String name, Type...parameters) {
		return getRoot().abstractDataType(name, parameters);
	}

	public Type concreteSyntaxType(String name, org.rascalmpl.ast.Type type) {
		return getRoot().concreteSyntaxType(name, type);
	}

	public Type concreteSyntaxType(String name, IConstructor symbol) {
		return getRoot().concreteSyntaxType(name, symbol);
	}

	public ConstructorFunction constructorFromTuple(AbstractAST ast, Evaluator eval, Type adt, String name, Type tupleType) {
		return getRoot().constructorFromTuple(ast, eval, adt, name, tupleType);
	}

	public ConstructorFunction constructor(AbstractAST ast, Evaluator eval, Type nodeType, String name, Object... childrenAndLabels ) {
		return getRoot().constructor(ast, eval, nodeType, name, childrenAndLabels);
	}

	public ConstructorFunction constructor(AbstractAST ast, Evaluator eval, Type nodeType, String name, Type... children ) {
		return getRoot().constructor(ast, eval, nodeType, name, children);
	}

	public Type aliasType(String name, Type aliased, Type...parameters) {
		return getRoot().aliasType(name, aliased, parameters);
	}

	public TypeStore getStore() {
		return getRoot().getStore();
	}

	public Map<String, Result<IValue>> getVariables() {
		Map<String, Result<IValue>> vars = new HashMap<String, Result<IValue>>();
		if (parent != null) {
			vars.putAll(parent.getVariables());
		}
		if (variableEnvironment != null) {
			vars.putAll(variableEnvironment);
		}
		return vars;
	}

	public List<Entry<String, OverloadedFunctionResult>> getFunctions() {
		ArrayList<Entry<String, OverloadedFunctionResult>> functions = new ArrayList<Entry<String, OverloadedFunctionResult>>();
		if (parent != null) {
			functions.addAll(parent.getFunctions());
		}
		if (functionEnvironment != null) {
			functions.addAll(functionEnvironment.entrySet());
		}
		return functions;
	}
	
	public Environment getParent() {
		return parent;
	}

	public Environment getCallerScope() {
		if (callerScope != null) {
			return callerScope;
		} else if (parent != null) {
			return parent.getCallerScope();
		}
		return null;
	}

	public ISourceLocation getCallerLocation() {
		if (callerLocation != null) {
			return callerLocation;
		} else if (parent != null) {
			return parent.getCallerLocation();
		}
		return null;
	}

	public Set<String> getImports() {
		return getRoot().getImports();
	}

	public void reset() {
		this.variableEnvironment = null;
		this.functionEnvironment = null;
		this.typeParameters = null;
		this.nameFlags = null;
	}

	public void declareProduction(Syntax x) {
		getRoot().declareProduction(x);
	}
	
	public ISet getProductions() {
		return getRoot().getProductions();
	}

	// TODO: We should have an extensible environment model that doesn't
	// require this type of checking, but instead stores all the info on
	// a name in one location...
	protected Environment getFlagsEnvironment(String name) {
		if (this.variableEnvironment != null) {
			if (this.variableEnvironment.get(name) != null) {
				if (this.nameFlags != null) {
					if (nameFlags.get(name) != null) {
						return this; // Found it at this level, return the environment
					}
				}
				return null; // Found the name, but no flags, so return null
			}
		}
		
		if (this.functionEnvironment != null) {
			if (this.functionEnvironment.get(name) != null) {
				if (this.nameFlags != null) {
					if (nameFlags.get(name) != null) {
						return this; // Found it at this level, return the environment
					}
				}
				return null; // Found the name, but no flags, so return null
			}
		}
		
		if (!isRootScope()) return parent.getFlagsEnvironment(name);
		return null;
	}
}

