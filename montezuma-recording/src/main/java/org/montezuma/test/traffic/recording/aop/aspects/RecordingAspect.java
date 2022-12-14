package org.montezuma.test.traffic.recording.aop.aspects;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.InitializerSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.montezuma.test.traffic.CallInvocationData;
import org.montezuma.test.traffic.Common;
import org.montezuma.test.traffic.InvocationData;
import org.montezuma.test.traffic.MustMock;
import org.montezuma.test.traffic.serialisers.SerialisationFactory;
import org.montezuma.test.traffic.serialisers.Serialiser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.TypeVariable;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.Map;

@Aspect
public abstract class RecordingAspect {


	// TODO - make the serialisation level configurable by System property or configuration file.
	// PERHAPS ALL OR PART OF THIS SHOULD BE IMPLEMENTED BY THE SPECIFIC SERIALISERS, as
	// every object contained by this object can be or not be Serializable.
	// That includes array/Collection/Map main objects.
	// 0 - Everything
	// 1 - Serializable classes only
	// 2 - Java primitives, Numbers and Strings only
	private final static int serialisationPolicy = 1;

	private static final boolean	behaviouralCapture														= true;
	private static final String		WITHIN_REGEX																	= "analysethis..*";
//	private static final String		CALL_INVOCATION_FILTER												= "((call(* *(..))) || (call(*.new(..))))" + (behaviouralCapture ? " && (!call(* " + WITHIN_REGEX + "(..))) && (!call(" + WITHIN_REGEX + ".new(..)))" : "") + " && within(" + WITHIN_REGEX + ")";
		private static final String		CALL_INVOCATION_FILTER												= "((call(* *(..))) || (call(*.new(..)))) && within(" + WITHIN_REGEX + ")";
	private static final String		BEFORE_EXECUTION_AND_NEW_INVOCATION_FILTER		= "((execution(* *(..))) || (execution(*.new(..)))) && within(" + WITHIN_REGEX + ")";
	private static final String		AFTER_EXECUTION_INVOCATION_FILTER							= "((execution(* *(..))) || (staticinitialization(*))) && within(" + WITHIN_REGEX + ")";
	private static final String		EXECUTION_INVOCATION_FILTER_AFTER_CONSTRUCTOR	= "((execution(*.new(..)))) && within(" + WITHIN_REGEX + ")";
	public static final String		ARGS_SEPARATOR																= ",";
	public static final String		METHOD_NAME_TO_ARGS_SEPARATOR									= "|";

	// @formatter:off
	private final ThreadLocal<LinkedList<InvocationData>>	threadLocalStackOfExecutionInvocationData
		= new ThreadLocal<LinkedList<InvocationData>>() {
				@Override
				protected LinkedList<InvocationData> initialValue() {
					return new LinkedList<InvocationData>();
				}
			};
	private final ThreadLocal<Serialiser>							serialiser
		= new ThreadLocal<Serialiser>() {
				@Override
				protected Serialiser initialValue() {
					return SerialisationFactory.getSerialiser();
				}
			};
	// TODO - change generics of threadLocalWasOutsideScopeStack so that you can just replace the topmost element, like to an AtomicInteger, but without thread context switching costs
	private final ThreadLocal<LinkedList<Boolean>>					threadLocalWasOutsideScopeStack
		= new ThreadLocal<LinkedList<Boolean>>() {
				@Override
				protected LinkedList<Boolean> initialValue() {
					final LinkedList<Boolean> list = new LinkedList<Boolean>();
					list.push(Boolean.TRUE);
					return list;
				}
			};
	// @formatter:on

	@Pointcut//(BEFORE_EXECUTION_AND_NEW_INVOCATION_FILTER)
	public abstract void executionAndInstantiationPoint();

	@Before("executionAndInstantiationPoint()")
	public void logBeforeExecution(JoinPoint thisJoinPoint) throws IOException {
		if (RecordingAspectControl.instance.stop)
			return;
		if (behaviouralCapture) {
			final LinkedList<Boolean> wasOutsideScopeStack = threadLocalWasOutsideScopeStack.get();
			final boolean behaviouralExecution = wasOutsideScopeStack.peek();
			System.out.println("Behavioural stack (before exec/new) size:" + wasOutsideScopeStack.size() + ", last: " + behaviouralExecution);
			wasOutsideScopeStack.push(Boolean.FALSE);
			if (!behaviouralExecution)
				return; // The invocation comes from within the instrumented code: don't record it.
		}

		final Signature signature = thisJoinPoint.getSignature();
		final String methodSignatureString;
		if (signature instanceof MethodSignature) {
			methodSignatureString = getMethodSignatureString((MethodSignature) signature);
		} else if (signature instanceof ConstructorSignature) {
			if (thisJoinPoint.getThis().getClass() != signature.getDeclaringType()) {
				return; // Superclass of class under scrutiny. In the future, we should simply add this
								// invocation to the signature's declaring type stack-of-execution-InvocationData
			}
			methodSignatureString = getConstructorSignatureString((ConstructorSignature) signature);
		} else if (signature instanceof InitializerSignature) {
			methodSignatureString = signature.getName(); // <clinit>
		} else
			throw new IllegalStateException("Unexpected signature type: " + signature.getClass());
		final Object[] args = thisJoinPoint.getArgs();
		final int[] argIDs = getArgIDs(args);
		if (RecordingAspectControl.instance.log)
			System.out.print("\nBEFORE EXEC on " + "type: " + thisJoinPoint.getStaticPart().getKind() + ", " + thisJoinPoint.getSignature().getDeclaringType().getName() + ", method " + methodSignatureString
					+ ", n. args: " + args.length);
		InvocationData data = new InvocationData(new Date(), methodSignatureString, serialiseArgs(args), argIDs);
		threadLocalStackOfExecutionInvocationData.get().push(data);
		if (RecordingAspectControl.instance.log)
			System.out.println("InvocationData stack size after adding:" + threadLocalStackOfExecutionInvocationData.get().size());
	}

	private String getMethodSignatureString(MethodSignature signature) {
		final Class<?>[] parameterTypes = signature.getParameterTypes();
		final String name = signature.getName();
		return getSignatureString(parameterTypes, name);
	}

	private String getConstructorSignatureString(ConstructorSignature signature) {
		final Class<?>[] parameterTypes = signature.getParameterTypes();
		final String name = signature.getName();
		return getSignatureString(parameterTypes, name);
	}

	private String getSignatureString(final Class<?>[] parameterTypes, final String name) {
		final StringBuilder argTypes = new StringBuilder();
		if (parameterTypes.length > 0) {
			for (Class<?> paramType : parameterTypes) {
				argTypes.append(paramType.getName());
				argTypes.append(ARGS_SEPARATOR);
			}
			argTypes.setLength(argTypes.length() - ARGS_SEPARATOR.length());
		}
		return name + METHOD_NAME_TO_ARGS_SEPARATOR + argTypes;
	}

	@Pointcut//(EXECUTION_INVOCATION_FILTER_AFTER_CONSTRUCTOR)
	public abstract void instantiationPoint();

@AfterReturning(pointcut = "instantiationPoint()")
	public void logAfterConstructorReturning(JoinPoint thisJoinPoint) throws IOException {
		if (RecordingAspectControl.instance.stop)
			return;

		if (behaviouralCapture) {
			final LinkedList<Boolean> wasOutsideScopeStack = threadLocalWasOutsideScopeStack.get();
			System.out.println("Behavioural stack (after constructor, before popping) size:" + wasOutsideScopeStack.size() + ", last: " + wasOutsideScopeStack.peek());
			wasOutsideScopeStack.pop();
			final boolean behaviouralExecution = wasOutsideScopeStack.peek();
			System.out.println("Behavioural stack (after constructor, after popping) size:" + wasOutsideScopeStack.size() + ", last: " + behaviouralExecution);
			if (!behaviouralExecution)
				return; // The invocation comes from within the instrumented code: don't record it.
		}
		final Signature signature = thisJoinPoint.getSignature();
		if (signature instanceof ConstructorSignature) {
			if (thisJoinPoint.getThis().getClass() != signature.getDeclaringType()) {
				return; // Superclass of class under scrutiny. In the future, we should simply add this
				// invocation to the signature's declaring type stack-of-execution-InvocationData
			}
		}
		if (RecordingAspectControl.instance.log) {
			System.out.print("\nAFTER CONSTRUCTOR on " + "type: " + thisJoinPoint.getStaticPart().getKind() + ", " + signature.getDeclaringType().getName() + ", n. args: " + thisJoinPoint.getArgs().length);
			System.out.println("InvocationData stack size before popping:" + threadLocalStackOfExecutionInvocationData.get().size());
		}

		final InvocationData data = threadLocalStackOfExecutionInvocationData.get().pop();
		store(thisJoinPoint, data);
	}

	@Pointcut//(AFTER_EXECUTION_INVOCATION_FILTER)
	public abstract void executionPoint();

	@AfterReturning(pointcut = "executionPoint()", returning = "result")
	public void logAfterExecutionReturning(JoinPoint thisJoinPoint, Object result) throws IOException {
		if (RecordingAspectControl.instance.stop)
			return;

		if (behaviouralCapture) {
			final LinkedList<Boolean> wasOutsideScopeStack = threadLocalWasOutsideScopeStack.get();
			System.out.println("Behavioural stack (after returning, before popping) size:" + wasOutsideScopeStack.size() + ", last: " + wasOutsideScopeStack.peek());
			wasOutsideScopeStack.pop();
			final boolean behaviouralExecution = wasOutsideScopeStack.peek();
			System.out.println("Behavioural stack (after returning, after popping) size:" + wasOutsideScopeStack.size() + ", last: " + behaviouralExecution);
			if (!behaviouralExecution)
				return; // The invocation comes from within the instrumented code: don't record it.
		}

		if (RecordingAspectControl.instance.log) {
			System.out.print("\nAFTER EXEC on " + "type: " + thisJoinPoint.getStaticPart().getKind() + ", " + thisJoinPoint.getSignature().getDeclaringType().getName() + ", method "
					+ thisJoinPoint.getSignature().toString() + ", n. args: " + thisJoinPoint.getArgs().length);
			System.out.println("InvocationData stack size before popping:" + threadLocalStackOfExecutionInvocationData.get().size());
		}
		final InvocationData data = threadLocalStackOfExecutionInvocationData.get().pop();
		if (((MethodSignature) thisJoinPoint.getSignature()).getReturnType() != void.class)
			data.serialisedReturnValue = serialiseArg(result);
		data.returnValueID = System.identityHashCode(result);

		store(thisJoinPoint, data);
	}

	@AfterThrowing(pointcut = "executionPoint()", throwing = "throwable")
	public void logAfterExecutionThrowing(JoinPoint thisJoinPoint, Throwable throwable) throws IOException {
		if (RecordingAspectControl.instance.stop)
			return;
		if (behaviouralCapture) {
			final LinkedList<Boolean> wasOutsideScopeStack = threadLocalWasOutsideScopeStack.get();
			System.out.println("Behavioural stack (after throwing, before popping) size:" + wasOutsideScopeStack.size() + ", last: " + wasOutsideScopeStack.peek());
			wasOutsideScopeStack.pop();
			final boolean behaviouralExecution = wasOutsideScopeStack.peek();
			System.out.println("Behavioural stack (after throwing, after popping) size:" + wasOutsideScopeStack.size() + ", last: " + wasOutsideScopeStack.peek());
			if (!behaviouralExecution)
				return; // The invocation comes from within the instrumented code: don't record it.
		}

		if (RecordingAspectControl.instance.log) {
			System.out.println("++++++ Exception thrown: +++++++++");
			throwable.printStackTrace();
			System.out.print("\nAFTER-THROWING on " + "type: " + thisJoinPoint.getStaticPart().getKind() + ", " + thisJoinPoint.getSignature().getDeclaringType().getName() + ", method "
					+ thisJoinPoint.getSignature().toString() + ", n. args: " + thisJoinPoint.getArgs().length);
			System.out.println("InvocationData stack size before popping:" + threadLocalStackOfExecutionInvocationData.get().size());
		}
		final InvocationData data = threadLocalStackOfExecutionInvocationData.get().pop();
		data.serialisedThrowable = serialiseArg(throwable);
		data.returnValueID = System.identityHashCode(throwable);
		store(thisJoinPoint, data);
	}

	@Pointcut//(CALL_INVOCATION_FILTER)
	public abstract void callPoint();

	@Before("callPoint()")
	public void logBeforeCall(JoinPoint thisJoinPoint) throws IOException {
		if (RecordingAspectControl.instance.stop)
			return;

		if (!shouldRecordCall(thisJoinPoint))
			return;

		final LinkedList<InvocationData> stackOfExecutionData = threadLocalStackOfExecutionInvocationData.get();
		if (stackOfExecutionData.size() == 0)
			return; // STATIC CODE NOT SUPPORTED YET
		final Signature signature = thisJoinPoint.getSignature();
		final String methodSignatureString;
		if (signature instanceof MethodSignature) {
			methodSignatureString = getMethodSignatureString((MethodSignature) signature);
		} else if (signature instanceof ConstructorSignature) {
			methodSignatureString = getConstructorSignatureString((ConstructorSignature) signature);
		} else if (signature instanceof InitializerSignature) {
			methodSignatureString = signature.getName(); // <clinit>
		} else
			methodSignatureString = "UNEXP";// throw new
																			// IllegalStateException("Unexpected signature type: " +
																			// signature.getClass());
		final Object[] args = thisJoinPoint.getArgs();
		final int[] argIDs = getArgIDs(args);
		// TODO: workaround for declaring type: format(Object o) on DecimalFormat is attributed to
		// java.text.NumberFormat instead of the ancestor java.text.Format
		final Class<?> declaringType = signature.getDeclaringType();
		// final Class declaringType = thisJoinPoint.getTarget().getClass().getMethod(signature.getName(),
		// parameterTypes).getDeclaringType();
		if (RecordingAspectControl.instance.log) {
			System.out.print("\nBEFORE CALL on " + "type: " + thisJoinPoint.getStaticPart().getKind() + ", " + thisJoinPoint.getSignature().getDeclaringType().getName() + ", to signature type: " + declaringType
					+ ", to target: " + (thisJoinPoint.getTarget() == null ? null : thisJoinPoint.getTarget().getClass().getName()) + ", method " + methodSignatureString + ", n. args: " + args.length);
			System.out.println("InvocationData stack size before peeking:" + stackOfExecutionData.size());
		}
		final Object thiz = thisJoinPoint.getThis();
		Class<?> thisClass = (thiz == null ? thisJoinPoint.getSourceLocation().getWithinType() : thiz.getClass());
		CallInvocationData callInvocationData = new CallInvocationData(declaringType, thisJoinPoint.getTarget(), new Date(), methodSignatureString, serialiseArgs(args), argIDs, signature.getModifiers(), thisClass);
		final InvocationData thisFrameInvocationData = stackOfExecutionData.peek();
		thisFrameInvocationData.addCall(callInvocationData);
		if (behaviouralCapture) {
			System.out.println("Behavioural stack (before call, before adding) size:" + threadLocalWasOutsideScopeStack.get().size() + ", last: " + threadLocalWasOutsideScopeStack.get().peek());
			threadLocalWasOutsideScopeStack.get().push(Boolean.TRUE); // Going out of the instrumented code
		}
	}

	protected byte[][] serialiseArgs(final Object[] args) throws IOException {
		byte[][] serialisedArgs = new byte[args.length][];
		for (int i = 0; i < args.length; i++) {
			Object arg = args[i];
			serialisedArgs[i] = serialiseArg(arg);
		}
		return serialisedArgs;
	}

	protected byte[] serialiseArg(Object arg) throws IOException {
		final Object toSerialise;
		if (arg == null) {
			toSerialise = null;
		} else if (arg.getClass().isArray()) {
			// It shouldn't throw a ClassCastException, because shouldSerialise(arg) should return true for arrays of
			// primitives.
			final Object[] arrayArg = (Object[]) arg;
			final Object[] arrayToSerialise = new Object[arrayArg.length];
			for (int i = 0; i < arrayArg.length; i++)
				arrayToSerialise[i] = serialiseArg(arrayArg[i]);
			toSerialise = arrayToSerialise;
		} else if (shouldSerialise(arg)) {
			toSerialise = arg;
		} else
			toSerialise = new MustMock(arg);

		try {
			return serialiser.get().serialise(toSerialise);
		} catch (IllegalArgumentException iae) {
			iae.printStackTrace(); // Bug in Kryo means we cannot store the actual data: java.lang.IllegalArgumentException: The type must be an enum: ... at com.esotericsoftware.kryo.serializers.DefaultSerializers$EnumSerializer.<init>(DefaultSerializers.java:315) ... 4402 more Caused by: java.lang.reflect.InvocationTargetException at sun.reflect.GeneratedConstructorAccessor131.newInstance(Unknown Source) ... at com.esotericsoftware.kryo.factories.ReflectionSerializerFactory.makeSerializer(ReflectionSerializerFactory.java:41)
			return serialiser.get().serialise(null);
		}
	}

	private boolean shouldSerialise(Object arg) {
		if (serialisationPolicy == 0)
			return true;

		if (arg == null)
			return true;

		final Class<? extends Object> argClass = arg.getClass();
		if (serialisationPolicy == 1) {
			// The array/Collection/Map must be Serialisable...
			if (!(arg instanceof Serializable))
				return false;

			// ...but its objects must too!!
			// TODO - The following checks are rough, as each single object can be or not be Serializable,
			// but for now this does the job
			if (argClass.isArray()) {
				final Class<?> componentType = argClass.getComponentType();
				if (componentType.isPrimitive())
					return true;

				return Serializable.class.isAssignableFrom(componentType);
			}

			// TODO - The following checks are rough, as each single object can be or not be Serializable,
			// but for now this does the job
			if (Collection.class.isAssignableFrom(argClass))
				return Serializable.class.isAssignableFrom(argClass.getTypeParameters()[0].getGenericDeclaration());

			if (arg instanceof Map<?, ?>) {
				final TypeVariable<?>[] typeParameters2 = argClass.getTypeParameters();
				@SuppressWarnings("unchecked") final TypeVariable<Class<? extends Object>>[] typeParameters = (TypeVariable<Class<? extends Object>>[]) typeParameters2;
				return Serializable.class.isAssignableFrom(typeParameters[0].getGenericDeclaration()) && Serializable.class.isAssignableFrom(typeParameters[1].getGenericDeclaration());
			}

			return true;
		}

		if (argClass.isPrimitive())
			return true;

		if (Common.primitiveClassesSet.contains(argClass))
			return true;

		if (argClass == String.class)
			return true;

		return false;
	}

	private int[] getArgIDs(Object[] args) {
		int[] ids = new int[args.length];

		for (int i = 0; i < args.length; i++) {
			ids[i] = System.identityHashCode(args[i]);
		}

		return ids;
	}

	@AfterReturning(pointcut = "callPoint()", returning = "result")
	public void logAfterCallReturning(JoinPoint thisJoinPoint, Object result) throws IOException {
		if (RecordingAspectControl.instance.stop)
			return;

		if (!shouldRecordCall(thisJoinPoint))
			return;

		final LinkedList<InvocationData> stackOfExecutionData = threadLocalStackOfExecutionInvocationData.get();
		if (stackOfExecutionData.size() == 0)
			return; // INITIALISER CODE NOT SUPPORTED YET
		// TODO - Is this hack working at all? Fix this hack: not sure why CALL_INVOCATION_FILTER is
		// catching static initialisation too!!!
		final Signature signature = thisJoinPoint.getSignature();
		if (signature instanceof InitializerSignature)
			return;
		if (RecordingAspectControl.instance.log) {
			System.out.print("\nAFTER CALL on " + "type: " + thisJoinPoint.getStaticPart().getKind() + ", " + signature.getDeclaringType().getName() + ", to signature type: "
					+ signature.getDeclaringType().getName() + ", to target: " + (thisJoinPoint.getTarget() == null ? null : thisJoinPoint.getTarget().getClass().getName()) + ", method " + signature.toString()
					+ ", n. args: " + thisJoinPoint.getArgs().length);
			System.out.println("InvocationData stack size before peeking:" + stackOfExecutionData.size());
		}
		final InvocationData data = stackOfExecutionData.peek().getLastCall();
		if (!(signature instanceof MethodSignature) || ((MethodSignature) signature).getReturnType() != void.class)
			data.serialisedReturnValue = serialiseArg(result);
		data.returnValueID = System.identityHashCode(result);
		if (behaviouralCapture) {
			System.out.println("Behavioural stack (after returning, before popping) size:" + threadLocalWasOutsideScopeStack.get().size() + ", last: " + threadLocalWasOutsideScopeStack.get().peek());
			threadLocalWasOutsideScopeStack.get().pop(); // Coming back into instrumented code
		}
	}

	@AfterThrowing(pointcut = "callPoint()", throwing = "throwable")
	public void logAfterCallThrowing(JoinPoint thisJoinPoint, Throwable throwable) throws IOException {
		if (RecordingAspectControl.instance.stop)
			return;

		if (!shouldRecordCall(thisJoinPoint))
			return;

		if (thisJoinPoint.getSignature() instanceof InitializerSignature)
			return; // INITIALISER CODE NOT SUPPORTED YET
		if (RecordingAspectControl.instance.log) {
			System.out.println("++++++ Exception thrown: +++++++++");
			throwable.printStackTrace();
			System.out.print("\nAFTER-THROWING on " + "type: " + thisJoinPoint.getStaticPart().getKind() + ", " + thisJoinPoint.getSignature().getDeclaringType().getName() + ", to signature type: "
					+ thisJoinPoint.getSignature().getDeclaringType().getName() + ", to target: " + (thisJoinPoint.getTarget() == null ? null : thisJoinPoint.getTarget().getClass().getName()) + ", method "
					+ thisJoinPoint.getSignature().toString() + ", n. args: " + thisJoinPoint.getArgs().length);
			System.out.println("InvocationData stack size before peeking:" + threadLocalStackOfExecutionInvocationData.get().size());
		}
		final InvocationData data = threadLocalStackOfExecutionInvocationData.get().peek().getLastCall();
		data.serialisedThrowable = serialiseArg(throwable);
		data.returnValueID = System.identityHashCode(throwable);
		if (behaviouralCapture) {
			System.out.println("Behavioural stack (after throwin, before popping) size:" + threadLocalWasOutsideScopeStack.get().size() + ", last: " + threadLocalWasOutsideScopeStack.get().peek());
			threadLocalWasOutsideScopeStack.get().pop(); // Coming back into instrumented code
		}
	}

	private boolean shouldRecordCall(JoinPoint thisJoinPoint) {
		if (!behaviouralCapture)
			return true;

		final Object thiz = thisJoinPoint.getThis();
		Class<?> thisClass = (thiz == null ? thisJoinPoint.getSourceLocation().getWithinType() : thiz.getClass());
		boolean isWithin = isWithin(thisClass);
		boolean isCallingWithin = isCallingWithin(thisJoinPoint);

		return isWithin != isCallingWithin;
	}

	private boolean isCallingWithin(JoinPoint thisJoinPoint) {
		return isWithin(thisJoinPoint.getSignature().getDeclaringType());
	}

	private boolean isWithin(Class<?> clazz) {
		return clazz.getName().matches(WITHIN_REGEX);
	}

	private void store(JoinPoint thisJoinPoint, InvocationData data) throws FileNotFoundException, IOException {
		final Object thiz = thisJoinPoint.getTarget();
		final Class<?> clazz = thisJoinPoint.getSignature().getDeclaringType();
		File file = new File(RecordingAspectControl.instance.recordingDir, clazz.getCanonicalName() + "@" + System.identityHashCode(thiz));
		if (RecordingAspectControl.instance.log)
			System.out.println("WRITING TO " + file.getAbsolutePath());
		FileOutputStream fos = new FileOutputStream(file, true);
		try {
			serialiser.get().serialise(fos, data);
		}
		finally {
			try {
				fos.close();
			} catch (Throwable t) {
				// Intentionally empty
			}
		}
		InvocationData.printSingleInvocationDataSize(data);
		if (RecordingAspectControl.instance.log)
			System.out.println("FILE SIZE: " + file.length());
	}
}
