package org.montezuma.test.traffic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Common {
	public static final String	ARGS_SEPARATOR								= ",";
	public static final String	METHOD_NAME_TO_ARGS_SEPARATOR	= "|";

	public static Set<Class<?>>	primitiveClassesSet						= new HashSet<Class<?>>();
	static {
		for (Class<?> clazz : new Class<?>[] { Boolean.class, Character.class, Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class, Void.class })
				primitiveClassesSet.add(clazz);
	}

	public static Map<String, Class<?>>	primitiveClasses							= new HashMap<String, Class<?>>();
	static {
		for (Class<?> clazz : new Class<?>[] { boolean.class, char.class, byte.class, short.class, int.class, long.class, float.class, double.class, void.class })
				primitiveClasses.put(clazz.toString(), clazz);
	}
}
