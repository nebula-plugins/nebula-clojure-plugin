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

package nebula.plugin.clojuresque.tasks

import org.gradle.api.tasks.Upload

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ClojureUploadConvention {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClojureUploadConvention)

    private Upload upload

    public ClojureUploadConvention(Upload upload) {
        this.upload = upload
    }

    public void clojarsDeploy() {
        Util.deprecationWarning(LOGGER, "clojarsDeploy()", "clojars.deploy()")
        LOGGER.warn("(provided by new clojars plugin)")

        upload.doLast {
            String pomName = project.buildDir.path + "/" +
                project.mavenPomDir.path + "/" +
                "pom-" + upload.configuration.name + ".xml"

            project.pom().writeTo(pomName)
            project.exec {
                executable = '/usr/bin/scp'
                args = project.files(upload.artifacts)*.path +
                    [ project.file(pomName).path,
                      'clojars@clojars.org:' ]
            }
        }
    }
}
