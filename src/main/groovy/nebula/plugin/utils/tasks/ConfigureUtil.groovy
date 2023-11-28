package nebula.plugin.utils.tasks

/**
 * A utility class to help configuring objects.
 *
 * @author Meikel Brandmeyer
 */
class ConfigureUtil {
    /**
     * Configures the <code>target</code> object. It sets it as the delegate
     * of the <code>configureFn</code> closure and calls the latter. Passes
     * the target object also as first argument to the closure should it
     * support it.
     *
     * @param  target      the object to configure
     * @param  configureFn a closure to be executed with <code>target</code> as delegate
     * @return             <code>target</code>
     */
    static configure(Object target, Closure configureFn={}) {
        def fn = configureFn.clone()

        fn.resolveStrategy = Closure.DELEGATE_FIRST
        fn.delegate        = target

        if (fn.maximumNumberOfParameters == 0)
            fn.call()
        else
            fn.call(target)

        target
    }
}
