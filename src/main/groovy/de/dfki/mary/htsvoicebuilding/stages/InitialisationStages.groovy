package de.dfki.mary.htsvoicebuilding.stages

// Grade imports
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Zip

// Task imports
import de.dfki.mary.htsvoicebuilding.stages.task.GenerateMLFTask
import de.dfki.mary.htsvoicebuilding.stages.task.GenerateSCPTask
import de.dfki.mary.htsvoicebuilding.stages.task.GenerateListTask
import de.dfki.mary.htsvoicebuilding.stages.task.GeneratePrototypeTask
import de.dfki.mary.htsvoicebuilding.stages.task.GenerateTrainingConfigurationTask
import de.dfki.mary.htsvoicebuilding.stages.task.InitModelsTask


class InitialisationStages {

    /****************************************************************************************
     **
     ****************************************************************************************/
    public static void addTasks(Project project)
    {
        project.task('generateSCPFile', type: GenerateSCPTask) {
            dependsOn "configurationVoiceBuilding"
            description "Generate the SCP file which contains the list of files used to train the models"
            list_basenames = new File(project.configuration.user_configuration.data.list_files)
            scp_file = new File(project.train_scp)
        }

        project.task("generateMonoMLF", type: GenerateMLFTask) {
            dependsOn "configurationVoiceBuilding"
            description "Generate the Master Label File for Monophone training"
            mlf_file = new File(project.mono_mlf_filename)
            lab_dir = new File(project.configuration.user_configuration.data.mono_lab_dir)
        }

        project.task('generateMonophoneList', type: GenerateListTask) {
            description "Generate the list of monophone labels"

            lab_dir = new File(project.configuration.user_configuration.data.mono_lab_dir)
            list_basenames = new File(project.configuration.user_configuration.data.list_files)
            list_file = new File(project.mono_list_filename)
        }

        project.task('generatePrototype', type: GeneratePrototypeTask) {
            description "Generate the HMM prototype file"
            prototype_template = new File(project.template_dir, "proto")
            prototype_file = new File(project.proto_dir, "proto")
        }

        project.task("generateTrainingConfigurationFile", type: GenerateTrainingConfigurationTask) {
            description "Generate the training configuration file"
            dependsOn "configurationVoiceBuilding"
            configuration_template = new File(project.template_dir, "train.cfg")
            configuration_file = new File(project.config_dir, "train.cfg")
        }

        project.task("generateNVCConfigurationFile") {
            dependsOn "configurationVoiceBuilding"

            doLast {
                // nvf.cfg
                project.copy {
                    from project.template_dir
                    into project.config_dir

                    include "nvf.cfg"
                    rename { file -> (new File(project.non_variance_config_filename)).name }
                }
            }
        }

        project.task("generateMOCCCMPConfigurationFiles") {
            dependsOn "configurationVoiceBuilding"

            doLast {
                project.configuration.user_configuration.models.cmp.streams.each { stream ->
                    def binding = [mocc : stream.mocc]
                    project.copy {
                        from project.template_dir
                        into project.config_dir

                        include "mocc.cfg"
                        rename { file -> stream.name + ".cfg" }

                        expand(binding)
                    }
                }
            }
        }

        project.task("generateMOCCDURConfigurationFile") {
            dependsOn "configurationVoiceBuilding"

            doLast {
                def binding = [mocc : project.configuration.user_configuration.models.dur.mocc]
                project.copy {
                    from project.template_dir
                    into project.config_dir

                    include "mocc.cfg"
                    rename { file -> "dur.cfg" }

                    expand(binding)
                }
            }
        }

        project.task('initModels', type: InitModelsTask)
        {
            // FIXME: refactor
            dependsOn "generateTrainingConfigurationFile"
            dependsOn "generateNVCConfigurationFile"

            // logging.captureStandardOutput LogLevel.INFO
            // logging.captureStandardError LogLevel.ERROR
            scp_file = project.generateSCPFile.scp_file
            prototype_file = project.generatePrototype.prototype_file
            vfloor_cmp_file = new File(project.cmp_model_dir + "/vFloors")
            init_cmp_file = new File(project.cmp_model_dir + "/init.mmf")
            average_cmp_file = new File(project.cmp_model_dir + "/average.mmf")

            vfloor_dur_file = new File(project.dur_model_dir + "/vFloors")
            init_dur_file = new File(project.dur_model_dir + "/init.mmf")
            average_dur_file = new File(project.dur_model_dir + "/average.mmf")


            vfloor_dur_template_file = new File("$project.template_dir/vfloordur")
            average_dur_template_file = new File("$project.template_dir/average_dur.mmf")

        }
    }
}
