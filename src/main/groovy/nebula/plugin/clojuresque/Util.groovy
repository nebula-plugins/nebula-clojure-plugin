/*-
 * Copyright 2009-2015 © Meikel Brandmeyer.
 * All rights reserved.
 *
 * Licensed under the EUPL V.1.1 (cf. file EUPL-1.1 distributed with the
 * source code.) Translations in other european languages available at
 * https://joinup.ec.europa.eu/software/page/eupl.
 *
 * Alternatively, you may choose to use the software under the MIT license
 * (cf. file MIT distributed with the source code).
 */

package nebula.plugin.clojuresque

import org.slf4j.Logger

import us.bpsm.edn.Keyword
import us.bpsm.edn.printer.Printers

class Util {
    static final List<String> SOURCE_EXTENSIONS = Arrays.asList(".clj", ".cljs")

    static Properties properties(plugin) {
        def props = new Properties()

        Util.class.
            getResourceAsStream("${plugin}.properties").
            withReader("UTF-8") { props.load it }

        return props
    }

    static deprecationWarning(Logger l, String o, String n) {
        l.warn(String.format("'%s' is deprecated and will go away in a future version. Please use '%s' instead.", o, n))
    }

    static camelCaseToSnakeCase(String camelCase) {
        Keyword.newKeyword(
            camelCase.replaceAll("([A-Z])", "-\$1").toLowerCase()
        )
    }

    static camelCaseToSnakeCase(Map camelCase) {
        camelCase.collectEntries { k, v ->
            [ camelCaseToSnakeCase(k),
              (v instanceof Map) ? camelCaseToSnakeCase(v) : v ]
        }
    }

    static optionsToStream(options) {
        def outWriter  = new StringWriter()
        def ednPrinter = Printers.newPrinter(outWriter)
        ednPrinter.printValue(camelCaseToSnakeCase(options))

        new ByteArrayInputStream(outWriter.toString().getBytes("UTF-8"))
    }

    static relativizePath(baseDir, absolutePath) {
        def baseDirS      = baseDir.path
        def absolutePathS = absolutePath.path

        (absolutePathS.startsWith(baseDirS)) ?
            absolutePathS.substring(baseDirS.length() + 1) :
            null
    }

    static toInputStream(InputStream stream) {
        return stream
    }

    static toInputStream(String fileName) {
        return new ByteArrayInputStream(fileName.getBytes("UTF-8"))
    }

    static toInputStream(File file) {
        return new FileInputStream(file)
    }

    static toInputStream(URL resource) {
        if ("file" == resource.protocol)
            return toInputStream(new File(resource.file))
        else
            return resource.openStream()
    }

    static toInputStream(List l) {
        return l.reverse().collect { toInputStream(it) }.inject { t, h ->
            new SequenceInputStream(h, t)
        }
    }

    static List<String> namespaceFile(String pattern) {
        SOURCE_EXTENSIONS.collect { pattern.replaceAll("-", "_").replaceAll("\\.", "/") + it }
    }
}
