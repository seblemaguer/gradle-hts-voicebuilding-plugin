package de.dfki.mary.htsvoicebuilding.stages.task.dnn

// Template import
import groovy.text.*

// Inject
import javax.inject.Inject;

// Worker import
import org.gradle.workers.*;

// Gradle task related
import org.gradle.api.Action;
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

/**
 *  Task which Description
 *
 */
public class GenerateTrainingConfigFileTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The configuration template file */
    @InputFile
    final RegularFileProperty template_file = newInputFile()

    /** The question -> configuration file */
    @InputFile
    final RegularFileProperty qconf_file = newInputFile()

    /** The produced configuration file */
    @OutputFile
    final RegularFileProperty configuration_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateTrainingConfigFileTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual generation method
     *
     */
    @TaskAction
    public void generate() {
        // Submit the execution
        workerExecutor.submit(GenerateTrainingConfigFileWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        template_file.getAsFile().get(),
                        qconf_file.getAsFile().get(),
                        configuration_file.getAsFile().get(),
                        project.vb_configuration
                    );
                }
            });
    }
}

/**
 *  Worker to Description
 *
 */
class GenerateTrainingConfigFileWorker implements Runnable {

    /** Configuration object */
    private Object configuration;

    /** The configuration template file */
    private File template_file;

    /** The question -> configuration file */
    private File qconf_file;

    /** The produced configuration file */
    private File configuration_file;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public GenerateTrainingConfigFileWorker(File template_file, File qconf_file,
                                            File configuration_file, Object configuration) {
        // Inputs
        this.template_file = template_file;
        this.qconf_file = qconf_file;

        // Outputs
        this.configuration_file = configuration_file;

        // Utilities
        this.configuration = configuration;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {

        def nb_input_features = 0
        qconf_file.eachLine { line ->
            if (line =~ /^[^#].*$/) { //All empty lines & lines starting by # should be ignored
                nb_input_features += 1
            }
        }
        def vec_size = 0
        configuration.models.ffo.streams.each { stream ->
            vec_size += (stream.order + 1) * stream.winfiles.size()
        }
        def dnn_settings = configuration.settings.dnn

        // Now adapt the proto template
        def binding = [
            num_input_units: nb_input_features,
            num_hidden_units: dnn_settings.num_hidden_units,
            num_output_units: vec_size,

            hidden_activation: dnn_settings.hidden_activation,
            output_activation: "Linear",
            optimizer: dnn_settings.optimizer,
            learning_rate: dnn_settings.learning_rate,
            keep_prob: dnn_settings.keep_prob,

            use_queue: dnn_settings.use_queue,
            queue_size: dnn_settings.queue_size,

            batch_size: dnn_settings.batch_size,
            num_epochs: dnn_settings.num_epochs,
            num_threads: dnn_settings.num_threads,
            random_seed: dnn_settings.random_seed,

            num_models_to_keep: dnn_settings.num_models_to_keep,

            log_interval: dnn_settings.log_interval,
            save_interval: dnn_settings.save_interval
        ]

        //
        def simple = new SimpleTemplateEngine()
        def source = template_file.text
        configuration_file.text = simple.createTemplate(source).make(binding).toString()
    }
}
