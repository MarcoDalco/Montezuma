package org.montezuma.test.traffic.writing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestMethod implements TextRenderer, ObjectDeclarationScope {
	public TestMethodOpening	opening;
	public CodeChunk					instantiationMethodPart;
	public List<CodeChunk>		codeChunks	= new ArrayList<>();
	public CodeChunk					closure;
	private Map<Integer, VariableDeclarationRenderer>			declaredVariables	= new HashMap<>();
	final ObjectDeclarationScope parentObjectDeclarationScope;
	
	public TestMethod(ObjectDeclarationScope parentObjectDeclarationScope) {
		this.parentObjectDeclarationScope = parentObjectDeclarationScope;
	}

	@Override
	public void render(StructuredTextFileWriter structuredTextFileWriter) {
		final Collection<Class<? extends Throwable>> allDeclaredThrowables = getAllDeclaredThrowables();
		opening.declaredThrowables.addAll(allDeclaredThrowables);

		opening.getRenderer().render(structuredTextFileWriter);
		
//		deduplicateVariableDeclarations();
		LinkedHashMap<Integer, InitCodeChunk> requiredInits = collectAllTheRequiredInits();
		for (InitCodeChunk codeChunk : requiredInits.values()) {
			int identityHashCode = codeChunk.identityHashCode;
			VariableDeclarationRenderer variableDeclarationRenderer = codeChunk.declarations.get(identityHashCode);
			if (variableDeclarationRenderer != null)
				addDeclaredObject(identityHashCode, variableDeclarationRenderer);
// TO CHECK - The following commented-out line seems reasonable, but at the moment it makes no difference.
//			codeChunk.parentObjectDeclarationScope = this;
			structuredTextFileWriter.appendChunk(2, codeChunk);
		}
		if (instantiationMethodPart != null)
			instantiationMethodPart.getRenderer().render(structuredTextFileWriter);

		List<CodeChunk> combinedCodeChunks = CodeChunk.tryCombine(codeChunks);
		for (CodeChunk codeChunk : combinedCodeChunks) {
			structuredTextFileWriter.appendChunk(2, codeChunk);
		}

		structuredTextFileWriter.appendChunk(1, closure);
	}

	private LinkedHashMap<Integer, InitCodeChunk> collectAllTheRequiredInits() {
		LinkedHashMap<Integer, InitCodeChunk> inits = new LinkedHashMap<>();

		if (instantiationMethodPart != null)
			inits.putAll(instantiationMethodPart.collectAllTheRequiredInits());
		for (CodeChunk chunk : codeChunks) {
			CodeChunk.mergeRequiredInits(inits, chunk);
		}

		return inits;
	}

	@Override
	public String toString() {
		return getClass().getName() + "@" + System.identityHashCode(this) + " [opening=" + opening + ", instantiationMethodPart=" + instantiationMethodPart + ", codeChunks=" + codeChunks + ", closure=" + closure + ", declaredVariables="
				+ declaredVariables + ", parentObjectDeclarationScope=" + ((Object) parentObjectDeclarationScope).toString() + "]";
	}

	private Collection<Class<? extends Throwable>> getAllDeclaredThrowables() {
		Set<Class<? extends Throwable>> allDeclaredExceptions = new HashSet<>();

		if (instantiationMethodPart != null)
			allDeclaredExceptions.addAll(instantiationMethodPart.getAllDeclaredThrowables());
		for (CodeChunk codeChunk : codeChunks) {
			allDeclaredExceptions.addAll(codeChunk.getAllDeclaredThrowables());
		}
		allDeclaredExceptions.addAll(closure.getAllDeclaredThrowables());

		return allDeclaredExceptions;
	}

	public ImportsContainer getAllImports() {
		ImportsContainer importsContainer = new ImportsContainer();

		for (Class<? extends Throwable> throwableClass : getAllDeclaredThrowables()) {
			importsContainer.addImport(new Import(throwableClass.getName()));
		}

		if (instantiationMethodPart != null)
			importsContainer.add(instantiationMethodPart.getAllImports());
		for (CodeChunk codeChunk : codeChunks) {
			importsContainer.add(codeChunk.getAllImports());
		}
		importsContainer.add(closure.getAllImports());

		return importsContainer;
	}

	public void preprocess() {
		opening.preprocess();
		if (instantiationMethodPart != null)
			instantiationMethodPart.preprocess();
		for (CodeChunk codeChunk : codeChunks) {
			codeChunk.preprocess();
		}
		closure.preprocess();
	}

	@Override
	public void addDeclaredObject(int identityHashCode, VariableDeclarationRenderer variableDeclarationRenderer) {
		declaredVariables.put(identityHashCode, variableDeclarationRenderer);
	}

	@Override
	public boolean declaresIdentityHashCode(int identityHashCode, Class<?> requiredClass) {
		{
			VariableDeclarationRenderer variableDeclarationRenderer = declaredVariables.get(identityHashCode);
			if ((variableDeclarationRenderer != null) && (variableDeclarationRenderer.declaresClass(requiredClass)))
				return true;
		}

		if ((opening != null) && (opening.declaresIdentityHashCode(identityHashCode, requiredClass)))
				return true;

		if ((instantiationMethodPart != null) && (instantiationMethodPart.declaresIdentityHashCode(identityHashCode, requiredClass)))
				return true;

		for (CodeChunk codeChunk : codeChunks)
			if (codeChunk.declaresIdentityHashCode(identityHashCode, requiredClass))
				return true;

		if ((closure != null) && (closure.declaresIdentityHashCode(identityHashCode, requiredClass)))
				return true;

			return false;
	}

	@Override
	public boolean declaresOrCanSeeIdentityHashCode(int identityHashCode, Class<?> requiredClass) {
		if (declaresIdentityHashCode(identityHashCode, requiredClass))
			return true;

		return parentObjectDeclarationScope.declaresOrCanSeeIdentityHashCode(identityHashCode, requiredClass);
	}

	@Override
	public VariableDeclarationRenderer getVisibleDeclarationRendererInScopeOrSubscopes(int identityHashCode, Class<?> requiredClass) {
		VariableDeclarationRenderer renderer;
		if (null != (renderer = declaredVariables.get(identityHashCode)))
			return renderer;

		if (null != (renderer = opening.getVisibleDeclarationRendererInScopeOrSubscopes(identityHashCode, requiredClass)))
			return renderer;

		if ((instantiationMethodPart != null) && ((renderer = instantiationMethodPart.getVisibleDeclarationRendererInScopeOrSubscopes(identityHashCode, requiredClass)) != null))
				return renderer;

		for (CodeChunk codeChunk : codeChunks)
			if (null != (renderer = codeChunk.getVisibleDeclarationRendererInScopeOrSubscopes(identityHashCode, requiredClass)))
				return renderer;

		if ((closure != null) && (null != (renderer = closure.getVisibleDeclarationRendererInScopeOrSubscopes(identityHashCode, requiredClass))))
				return renderer;

		return null;
	}

	@Override
	public VariableDeclarationRenderer getVisibleDeclarationRenderer(int identityHashCode, Class<?> requiredClass) {
		VariableDeclarationRenderer renderer = getVisibleDeclarationRendererInScopeOrSubscopes(identityHashCode, requiredClass);
		if (renderer != null)
			return renderer;

		return parentObjectDeclarationScope.getVisibleDeclarationRenderer(identityHashCode, requiredClass);
	}

	public void addParameter(int identityHashCode, VariableDeclarationRenderer variableDeclarationRenderer) {
		this.opening.addParameter(identityHashCode, variableDeclarationRenderer);
	}
}
