package org.montezuma.test.traffic.writing;

import org.montezuma.test.traffic.writing.VariableDeclarationRenderer.ComputableClassNameRendererPlaceholder;
import org.montezuma.test.traffic.writing.serialisation.SerialisationRendererFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RenderersStrategy {

	StructuredTextRenderer addRealParameter(CodeChunk codeChunk, Class<?> argClass, Object arg, int argID, ImportsContainer importsContainer, IdentityHashCodeGenerator identityHashCodeGenerator) throws ClassNotFoundException {
		final Class<?> declaredClass = /* TO CHECK - maybe Object.class instead of argClass, so that the actual declared type gets determined by the subsequent use? */ argClass;
		ExpressionRenderer deserialisationRenderer = getDeserialisationRenderer(codeChunk, arg, importsContainer, codeChunk, identityHashCodeGenerator);
		final VariableDeclarationRenderer renderer =
				new VariableDeclarationRenderer("final %s %s = %s;", argID, "given", declaredClass, importsContainer, ComputableClassNameRendererPlaceholder.instance, VariableDeclarationRenderer.NewVariableNameRendererPlaceholder.instance, new StructuredTextRenderer("(%s) %s", ComputableClassNameRendererPlaceholder.instance, deserialisationRenderer));
		codeChunk.addDeclaredObject(argID, renderer);
		return renderer;
	}

	private ExpressionRenderer getDeserialisationRenderer(CodeChunk codeChunk, Object object, ImportsContainer importsContainer, ObjectDeclarationScope objectDeclarationScope, IdentityHashCodeGenerator identityHashCodeGenerator) throws ClassNotFoundException {
		return SerialisationRendererFactory.getSerialisationRenderer().getDeserialisationCodeChunkFor(codeChunk, object, importsContainer, objectDeclarationScope, identityHashCodeGenerator);
	}

	StructuredTextRenderer buildInvocationParameters(CodeChunk mainCodeChunk, Object[] args, String[] argTypes, int[] argIDs, ImportsContainer importsContainer, MockingStrategy mockingStrategy, TestClassWriter testClassWriter, TestMethod testMethod) throws ClassNotFoundException {
	
		List<ExpressionRenderer> expressionRenderers = new ArrayList<>();
		final String argSeparator = ", ";
	
		StringBuffer argumentNames = new StringBuffer();
		for (int i = 0; i < args.length; i++) {
			Object arg = args[i];
			if (arg == null) {
				argumentNames.append("null");
			} else {
				Class<?> argClass = TrafficToUnitTestsWriter.primitiveTypes.get(argTypes[i]);
				if (argClass == null)
					argClass = Class.forName(argTypes[i]);
				final int argID = argIDs[i];
	
				// Here I reuse a previous initialisation, to avoid replacing the existing one, which needs to be "preprocessed" for other objects to use it. NOT IDEAL or is it correct? I'm now thinking the latter.
//				InitCodeChunk variableCodeChunk = mainCodeChunk.requiredInits.get(argID);
				VariableDeclarationRenderer variableDeclarationRenderer = mainCodeChunk.getVisibleDeclarationRenderer(argID, argClass);
				if ((variableDeclarationRenderer == null) ||
						!(MockingFrameworkFactory.getMockingFramework().canStubMultipleTypeWithOneStub() || variableDeclarationRenderer.desiresNoMoreThanSuperOrSubClassesOf(argClass))) {
					InitCodeChunk variableCodeChunk = createInitCodeChunk(arg, argClass, argID, "given", importsContainer, mockingStrategy, testClassWriter, testMethod, mainCodeChunk);
					mainCodeChunk.requiredInits.put(argID, variableCodeChunk);
					variableCodeChunk.generateRequiredInits();
				}
	
				// TODO - consider argTypes[i] for potential cast to specify the correct signature in case the target class has
				// overloaded methods where one parameter is more specific in one method than the other, e.g.: valueOf(Number)
				// and valueOf(Long). It will require this method to take the Declaring Type as an extra parameter.
				// In the above process, consider the class type of each argument as given in the argTypes String array,
				// i.e.: int.class versus Integer.class
				expressionRenderers.add(new ExistingVariableNameRenderer(argID, argClass, importsContainer, mainCodeChunk));
				mainCodeChunk.declaresOrCanSeeIdentityHashCode(argID, argClass); // To avoid inlining
				argumentNames.append("%s");
			}
			argumentNames.append(argSeparator);
		}
	
		final int argumentNamesLength = argumentNames.length();
		if (argumentNamesLength > 0) {
			argumentNames.setLength(argumentNamesLength - argSeparator.length());
		}
	
		return new StructuredTextRenderer(argumentNames.toString(), expressionRenderers.toArray(new ExpressionRenderer[expressionRenderers.size()]));
	}

	private InitCodeChunk createInitCodeChunk(final Object arg, final Class<?> argClass, final int argID, final String variableNamePrefix, ImportsContainer importsContainer, MockingStrategy mockingStrategy, TestClassWriter testClassWriter, TestMethod testMethod, ObjectDeclarationScope parentObjectDeclarationScope) {
		return new StandardInitCodeChunk(argID, arg, argClass, argID, variableNamePrefix, importsContainer, mockingStrategy, this, testClassWriter, testMethod, parentObjectDeclarationScope);
	}

	VariableNameRenderer buildExpectedReturnValue(CodeChunk codeChunk, Object returnValue, Class<?> returnValueDeclaredType, int identityHashCode, ImportsContainer importsContainer, MockingStrategy mockingStrategy, RenderersStrategy renderersStrategy, TestClassWriter testClassWriter, TestMethod testMethod) throws ClassNotFoundException, IOException {
		final Object arg = returnValue;
		final int argID = identityHashCode;
	
		// Here I reuse a previous initialisation, to avoid replacing the existing one, which needs to be "preprocessed" for other objects to use it. NOT IDEAL.
		// TODO - when not mocked, should this be a reconstructed object, instead?
		if (codeChunk.declaresOrCanSeeIdentityHashCode(identityHashCode, returnValueDeclaredType))
			return new ExistingVariableNameRenderer(identityHashCode, returnValueDeclaredType, importsContainer, codeChunk);
	
		InitCodeChunk returnValueInitCodeChunk = codeChunk.requiredInits.get(identityHashCode);
		if (returnValueInitCodeChunk == null) {
			returnValueInitCodeChunk = new StandardInitCodeChunk(argID, arg, returnValueDeclaredType, argID, "expected", importsContainer, mockingStrategy, renderersStrategy, testClassWriter, testMethod, codeChunk);
			codeChunk.requiredInits.put(identityHashCode, returnValueInitCodeChunk);
			returnValueInitCodeChunk.generateRequiredInits();
		}
	
		// final Class<? extends Object> returnValueClass = (returnValue instanceof MustMock ? ((MustMock)
		// returnValue).clazz : returnValue.getClass());
		// TODO - To be checked with downcast invocations, i.e. when the object - say it's returned by an expected
		// invocation - is then cast by the 'cut' to a more specific type and a method from that type is invoked. That would
		// be a good reason to use returnValueClass (returnValue.getClass()), but such class might not be visible (private
		// inner class).
		returnValueInitCodeChunk.declaresOrCanSeeIdentityHashCode(identityHashCode, returnValueDeclaredType); // To avoid inlining
		return new ExistingVariableNameRenderer(identityHashCode, returnValueDeclaredType, importsContainer, returnValueInitCodeChunk);
	}

}
