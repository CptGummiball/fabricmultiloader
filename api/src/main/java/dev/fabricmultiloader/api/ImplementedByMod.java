package dev.fabricmultiloader.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This type is implemented by mod code, so the framework must never add an abstract member to it.
 *
 * <p>The distinction from {@link ImplementedByFramework} is what makes the evolution rules
 * checkable rather than aspirational: an interface mods implement may only gain {@code default}
 * methods, while one the framework implements may grow freely. {@code ApiSurfaceTest} enforces that
 * every public API type carries exactly one of the two markers, so the question is answered before
 * a method is added rather than after somebody's build breaks.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ImplementedByMod {
}
