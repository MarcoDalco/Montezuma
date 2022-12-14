package org.montezuma.test.traffic.writing;

public class ClassNameRenderer implements ExpressionRenderer {
	public final Class<?>	clazz;
	private final ImportsContainer	importsContainer;

	public ClassNameRenderer(Class<?> clazz, ImportsContainer importsContainer) {
		this.clazz = clazz;
		this.importsContainer = importsContainer;
	}

	@Override
	public String render() {
		final String canonicalName = getRenderedClass().getCanonicalName();
		return importsContainer.imports(canonicalName) ? clazz.getSimpleName() : clazz.getCanonicalName();
	}

	Class<?> getRenderedClass() {
		return clazz;
	}

	@Override
	public String toString() {
		return "ClassNameRenderer [clazz=" + clazz + ", importsContainer=" + importsContainer.getClass().getName() + "@" + System.identityHashCode(importsContainer) + "]";
	}
}
