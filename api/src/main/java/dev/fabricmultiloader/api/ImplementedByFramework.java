package dev.fabricmultiloader.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This type is implemented by FabricMultiLoader, not by mod code, and may therefore gain members in
 * a minor release without breaking anyone.
 *
 * <p>Mods that implement one of these anyway are outside the compatibility promise. That is not a
 * rule for its own sake — it is the only way {@link ModContext} can grow a new accessor when a new
 * subsystem lands, which over a multi-year lifetime happens rather more often than one would like.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ImplementedByFramework {
}
