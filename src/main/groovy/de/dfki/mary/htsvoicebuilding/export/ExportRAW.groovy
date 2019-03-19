package de.dfki.mary.htsvoicebuilding.export

import groovy.json.* // To load the JSON configuration file

import java.nio.file.Files
import java.nio.file.Paths
import org.apache.commons.io.FileUtils

import org.gradle.api.Project

// HTS Engine export tasks import
import de.dfki.mary.htsvoicebuilding.export.task.raw.*

class ExportRAW {

    public static void addTasks(Project project) {
        def last_cluster = project.gradle.vb_configuration.settings.training.nb_clustering - 1
        def export_dir = new File("$project.buildDir/raw")
        export_dir.mkdirs()


        project.task("exportCMPRAWTrees", type: ExportCMPRAWTreeTask) {
            description "Convert the CMP set to HTS Engine voice compatible format"

            // Inputs
            list_file = project.generateFullList.list_file

            // FIXME: find a more direct part for the trees!
            def m_files = []
            project.gradle.vb_configuration.models.cmp.streams.each { stream ->
                for (i in 2..project.gradle.vb_configuration.models.global.nb_emitting_states+1) {
                    def f = project.file("${project.tree_dir}/fullcontext_${last_cluster}/${stream.name}_${i}.inf")
                    m_files.add(f)
                }
            }
            input_tree_files.setFrom(m_files)

            input_model_file = project.property("trainClusteredModels_${last_cluster}").trained_cmp_file

            // Outputs
            m_files = []
            project.gradle.vb_configuration.models.cmp.streams.each { stream ->
                def f = project.file("${project.hhed_script_dir}/cv_raw_${stream.kind}.hed")
                m_files.add(f)
            }
            script_files.setFrom(m_files)

            m_files = []
            project.gradle.vb_configuration.models.cmp.streams.each { stream ->
                def f = project.file("${export_dir}/trees/${stream.kind}.inf")
                m_files.add(f)
            }
            output_tree_files.setFrom(m_files)
        }

        project.task("exportRAWTrees") {
            dependsOn project.property("exportCMPRAWTrees")

            doLast {
                def tree_dir = new File("$export_dir/trees")
                project.copy {
                    from "${project.tree_dir}/dur.${last_cluster}.inf"
                    into tree_dir
                    rename { file -> "dur.inf"}
                }
            }
        }

        project.task("exportRAWHMMModels") {
            dependsOn project.property("trainClusteredModels_${last_cluster}")

            doLast {
                def model_dir = new File("$export_dir/models")
                model_dir.mkdirs()

                // Copy model files
                project.copy {
                    from project.property("trainClusteredModels_${last_cluster}").trained_cmp_file
                    into model_dir
                    rename { file -> "re_clustered_cmp.mmf" }
                }

                project.copy {
                    from project.property("trainClusteredModels_${last_cluster}").trained_dur_file
                    into model_dir
                    rename { file -> "re_clustered_dur.mmf" }
                }
            }
        }

        project.task("exportRAWLists") {
            dependsOn project.property("trainClusteredModels_${last_cluster}")

            doLast {
                project.copy {
                    from project.full_list_filename
                    into "$export_dir"
                    rename { file -> "full.list" }
                }
            }
        }

        project.task("exportRAWGV") {
            dependsOn project.property("trainGVClustered")

            doLast {
                def gv_dir = new File("$export_dir/gv")
                gv_dir.mkdirs()

                // Trees
                project.gradle.vb_configuration.models.cmp.streams.each { stream ->
                    project.copy {
                        from "${project.gv_dir}/${stream.name}.inf"
                        into gv_dir
                        rename { file -> "${stream.name}.inf"}
                    }
                }

                // Model
                project.copy {
                    from project.trainGVClustered.trained_model_file
                    into gv_dir
                    rename { file -> "clustered.mmf"}
                }

                // List
                project.copy {
                    from project.generateGVListFile.list_file
                    into gv_dir
                    rename { file -> "gv.list"}
                }
            }
        }

        project.task("exportRAWWindows") {
            doLast {

                (new File("$export_dir/win")).mkdirs()
                project.gradle.vb_configuration.models.cmp.streams.each { stream ->
                    stream.winfiles.each { win_file ->
                        project.copy {
                            from win_file
                            into "$export_dir/win/"
                            rename { file -> win_file.getName() }
                        }
                    }
                }
            }
        }


        project.task("exportRAWDNNModels") {
            dependsOn project.property("trainDNN")

            doLast {
                FileUtils.copyDirectory(project.trainDNN.model_dir.getAsFile().get(),
                                        new File("$export_dir/DNN/models"));

                FileUtils.copyDirectory(new File("$project.buildDir/dnn/var"),
                                        new File("$export_dir/DNN/var"));

                Files.copy(Paths.get(project.gradle.vb_configuration.settings.dnn.qconf),
                           Paths.get("$export_dir/DNN/qconf.conf"));
            }
        }


        project.task("exportRAWConfiguration") {
            doLast {
                def bos = new ByteArrayOutputStream()
                def oos = new ObjectOutputStream(bos)
                oos.writeObject(project.gradle.vb_configuration); oos.flush()

                def bin = new ByteArrayInputStream(bos.toByteArray())
                def ois = new ObjectInputStream(bin)
                def export_configuration = ois.readObject()
                export_configuration.remove("data")

                export_configuration.models.cmp.streams.each { stream ->
                    def win = []
                    stream.winfiles.each { win_file ->
                        win.add(win_file.getName())
                    }
                    stream.winfiles = win
                }

                // TODO: convert all files references to string

                (new File("$export_dir/config.json")).text = new JsonBuilder(export_configuration).toPrettyString()
            }
        }

        project.task("exportRAW") {
            dependsOn project.exportRAWTrees
            dependsOn project.exportRAWHMMModels
            dependsOn project.exportRAWWindows
            dependsOn project.exportRAWLists
            dependsOn project.exportRAWConfiguration

            if (project.gradle.vb_configuration.gv.use) {
                dependsOn project.exportRAWGV
            }

            if ((project.gradle.vb_configuration.settings.training.kind) &&
                (project.gradle.vb_configuration.settings.training.kind.equals("dnn")))  {
                dependsOn project.exportRAWDNNModels
            }
        }
    }
}
