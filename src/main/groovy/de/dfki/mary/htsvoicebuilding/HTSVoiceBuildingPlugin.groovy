package de.dfki.mary.htsvoicebuilding

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.MavenPlugin

import de.dfki.mary.htsvoicebuilding.export.*
import de.dfki.mary.htsvoicebuilding.stages.*


class HTSVoicebuildingPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply JavaPlugin
        project.plugins.apply MavenPlugin

        project.sourceCompatibility = JavaVersion.VERSION_1_8

        project.ext {

            // Debug switching
            def debug = false
            if (project.gradle.vb_configuration.settings.debug) {
                debug = true
            }

            // Data file
            cmp_dir = project.file("${project.buildDir}/cmp")
            ffo_dir = project.file("${project.buildDir}/ffo")

            // model directories
            train_scp = project.file("$project.buildDir/train.scp")

            // Create model and trees directories
            global_model_dir = project.file("$project.buildDir/models")
            cmp_model_dir = project.file("$project.buildDir/models/cmp")
            dur_model_dir = project.file("$project.buildDir/models/dur")
            proto_dir = project.file("$project.buildDir/models/proto")
            tree_dir = project.file("$project.buildDir/trees")
            proto_dir.mkdirs()
            cmp_model_dir.mkdirs()
            dur_model_dir.mkdirs()
            tree_dir.mkdirs()

            // List directories
            list_dir = project.file("$project.buildDir/lists")
            list_dir.mkdirs()
            mono_list_filename = project.file("$list_dir/mono.list")
            full_list_filename = project.file("$list_dir/full.list")

            // MLF Directory
            mlf_dir = project.file("$project.buildDir/mlf")
            mlf_dir.mkdirs()
            mono_mlf_filename = project.file("$mlf_dir/mono.mlf")
            full_mlf_filename = project.file("$mlf_dir/full.mlf")

            // Script directories
            hhed_script_dir = project.file("$project.buildDir/edfiles/")
            hhed_script_dir.mkdirs()

            // Configuration project
            config_dir = project.file("$project.buildDir/configs")
            config_dir.mkdirs()
            train_config_filename = project.file("$config_dir/train.cfg")
            non_variance_config_filename = project.file("$config_dir/nvf.cfg")

            // Specific initialisation directory
            project.file("${cmp_model_dir}/HRest").mkdirs()
            project.file("${dur_model_dir}/HRest").mkdirs()
            if (!project.gradle.vb_configuration.settings.daem.use) {
                project.file("${cmp_model_dir}/HInit").mkdirs()
                project.file("${dur_model_dir}/Hinit").mkdirs()
            }

            // Monophone part
            project.file("${cmp_model_dir}/monophone/init").mkdirs()
            project.file("${dur_model_dir}/monophone/init").mkdirs()
            project.file("${cmp_model_dir}/monophone/trained").mkdirs()
            project.file("${dur_model_dir}/monophone/trained").mkdirs()

            // full context
            for (int i=0; i<project.gradle.vb_configuration.settings.training.nb_clustering; i++) {
                project.file("${cmp_model_dir}/fullcontext_$i/init").mkdirs()
                project.file("${dur_model_dir}/fullcontext_$i/init").mkdirs()
                project.file("${cmp_model_dir}/fullcontext_$i/trained").mkdirs()
                project.file("${dur_model_dir}/fullcontext_$i/trained").mkdirs()
            }

            // GV
            if (project.gradle.vb_configuration.gv.use) {
                gv_dir        = project.file("$project.buildDir/gv/models")
                gv_data_dir   = project.file("$project.buildDir/gv/data")
                gv_fal_dir    = project.file("$project.buildDir/gv/fal")
                gv_lab_dir    = project.file("$project.buildDir/gv/labels")
                gv_scp_dir    = project.file("$project.buildDir/gv/scp") // FIXME

                project.file("${gv_dir}/init").mkdirs()
                project.file("${gv_dir}/trained").mkdirs()
                gv_data_dir.mkdirs()
                gv_fal_dir.mkdirs()
                gv_lab_dir.mkdirs()
                gv_scp_dir.mkdirs()
            } else {
                gv_dir        = null
                gv_data_dir   = null
                gv_fal_dir    = null
                gv_lab_dir    = null
                gv_scp_dir    = null
            }

            // DNN
            if ((project.gradle.vb_configuration.settings.training.kind) &&
                (project.gradle.vb_configuration.settings.training.kind.equals("dnn"))) {

                qconf = new File(project.gradle.vb_configuration.settings.dnn.qconf)
                train_dnn_scp = project.file("$project.buildDir/train_dnn.scp")
                dnn_dir = project.file("${project.buildDir}/dnn/models")
                alignment_dir = project.file("$project.buildDir/dnn/alignment")
                ffo_dir = project.file("$project.buildDir/ffo")
                ffi_dir = project.file("$project.buildDir/dnn/ffi")
                var_dir = project.file("$project.buildDir/dnn/var")

                dnn_dir.mkdirs()
                ffi_dir.mkdirs()
                var_dir.mkdirs()
            } else {
                train_dnn_scp = null
                dnn_dir = null
                alignment_dir = null
                ffo_dir = null
                ffi_dir = null
                var_dir = null
                qconf = null
            }

            // Template/config
            template_dir = project.file("$project.buildDir/tmp/templates")
            template_dir.mkdirs()
            def templates = [
                // HTS default
                'average_dur.mmf',
                'cxc.hed',
                'lvf.hed',
                'mmf2htsengine.hed',
                'mocc.cfg',
                'nvf.cfg',
                'proto',
                'protogv',
                'synth.cfg',
                'train.cfg',
                'train_dnn.cfg',
                'vfloordur',

                // HTS engine
                "htsvoice",
                "cv_hts_engine.hed",

                // MaryTTS
                'Config.java',
                'ConfigTest.java',
                'LoadVoiceIT.java',
                'voice-straight-hsmm.config',
                'database.config',
            ].collect {
                project.file "$project.template_dir/$it"
            }


            templates.each { outputFile ->
                outputFile.withOutputStream { stream ->
                    stream << getClass().getResourceAsStream("/de/dfki/mary/htsvoicebuilding/templates/$outputFile.name")
                }
            }

            // Util. scripts
            utils_dir = project.file("$project.buildDir/tmp/utils")
            utils_dir.mkdirs()
            def utils = ['HERest.pl',
                         'addhtkheader.pl',
                         'makefeature.pl',
                         'DNNDataIO.py',
                         'DNNDefine.py',
                         'DNNTraining.py'
            ].collect {
                project.file "$utils_dir/$it"
            }

            utils.each { outputFile ->
                outputFile.withOutputStream { stream ->
                    stream << getClass().getResourceAsStream("/de/dfki/mary/htsvoicebuilding/utils/$outputFile.name")
                }
            }

            // Instanciate HTS wrapper
            HTSWrapper.logger = project.logger
            def beams = project.gradle.vb_configuration.settings.training.beam.split() as List
            hts_wrapper = new HTSWrapper(beams, "$project.train_config_filename",
                                             project.gradle.vb_configuration.settings.training.wf,
                                             project.gradle.startParameter.getMaxWorkerCount(),
                                             "$project.buildDir/tmp/utils/HERest.pl", debug)
        }


        // Stages
        InitialisationStages.addTasks(project)
        MonophoneStages.addTasks(project)
        ContextStages.addTasks(project)
        GlobalVarianceStages.addTasks(project)
        DNNStages.addTasks(project)

        // Export
        ExportRAW.addTasks(project)
        ExportHTSEngine.addTasks(project)

        //Training task !
        addTrainTask(project)
    }

    /****************************************************************************************
     ** Run (Main) task
     ****************************************************************************************/
    private void addTrainTask(Project project) {
        project.task('train') {
            // RAW
            if ((project.gradle.vb_configuration.export.raw) &&
                (project.gradle.vb_configuration.export.raw.enabled)) {
                dependsOn "exportRAW"
            }

            // HTS engine
            if ((project.gradle.vb_configuration.export.hts_engine) &&
                (project.gradle.vb_configuration.export.hts_engine.enabled)) {
                dependsOn "exportHTSEngine"
            }
        }
    }
}
