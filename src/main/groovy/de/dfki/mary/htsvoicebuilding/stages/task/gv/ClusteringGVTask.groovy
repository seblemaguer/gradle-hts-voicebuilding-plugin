package de.dfki.mary.htsvoicebuilding.stages.task.gv

// For copying
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;

// Template import
import groovy.text.*;

// HTS Wrapper import
import de.dfki.mary.htsvoicebuilding.HTSWrapper;

// Inject
import javax.inject.Inject;

// Worker import
import org.gradle.workers.*;

// Gradle task related
import org.gradle.api.Action;
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.*


import de.dfki.mary.htsvoicebuilding.HTSWrapper;

/**
 *  Task to cluster the GV part
 *
 */
public class ClusteringGVTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;


    /** The list of labels file */
    @InputFile
    final RegularFileProperty list_file = newInputFile();

    /** The template for the tree generation script file */
    @InputFile
    final RegularFileProperty script_template_file = newInputFile();

    /** Question file */
    @InputFile
    final RegularFileProperty question_file = newInputFile(); // FIXME: split per stream

    /** Statistics GV file */
    @InputFile
    final RegularFileProperty stats_file = newInputFile();

    /** The input fullcontext model file */
    @InputFile
    final RegularFileProperty fullcontext_model_file = newInputFile();

    /** The output clustered file */
    @OutputFile
    final RegularFileProperty clustered_model_file = newOutputFile();

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public ClusteringGVTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual initialization method
     *
     */
    @TaskAction
    public void cluster() {
        // Submit the execution
        workerExecutor.submit(ClusteringGVWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(
                        script_template_file.getAsFile().get(),
                        question_file.getAsFile().get(),
                        list_file.getAsFile().get(),
                        stats_file.getAsFile().get(),
                        fullcontext_model_file.getAsFile().get(),
                        clustered_model_file.getAsFile().get(),
                        project.gv_dir,
                        project.hhed_script_dir,
                        project.hts_wrapper,
                        project.gradle.vb_configuration
                    );
                }
            });
    }
}

/**
 *  Worker class which cluster one kind of coefficient part of the GV part
 *
 */
class ClusteringGVWorker implements Runnable {


    /** The clustering template script file */
    private File script_template_file;

    /** The input question file */
    private File question_file;

    /** The list of label file */
    private File list_file;

    /** The input statistics file */
    private File stats_file;

    /** The input full context model file */
    private File fullcontext_model_file;

    /** The produced cluster model file */
    private File clustered_model_file;

    private File gv_dir;

    private File hhed_script_dir;

    /** The HTS wrapper helper object */
    private HTSWrapper hts_wrapper;

    /** Some parameters */
    private Object configuration;

    /**
     *  The contructor which initialize the worker
     *
     */
    @Inject
    public ClusteringGVWorker(File script_template_file, File question_file,
                              File list_file, File stats_file,
                              File fullcontext_model_file, File clustered_model_file,
                              File gv_dir, File hhed_script_dir,
                              HTSWrapper hts_wrapper, Object configuration) {
        this.script_template_file = script_template_file;
        this.question_file = question_file;
        this.list_file = list_file;
        this.stats_file = stats_file;
        this.fullcontext_model_file = fullcontext_model_file;
        this.clustered_model_file = clustered_model_file;
        this.gv_dir = gv_dir
        this.hhed_script_dir = hhed_script_dir
        this.hts_wrapper = hts_wrapper;
        this.configuration = configuration;
    }

    /**
     *  Run method which achieve the generateion/conversion
     *
     */
    @Override
    public void run() {

        // 1. copy
        Files.copy(fullcontext_model_file.toPath(), clustered_model_file.toPath(),
                   StandardCopyOption.REPLACE_EXISTING);

        // Prepare parallelism part !
        int stream_id = 1;
        for (def stream: configuration.models.cmp.streams) {

            // 1. generate HHEd scripts (FIXME: output file)
            def questions = question_file.text
            def streamline = "TB " + stream.gv.thr + " gv_" + stream.name +  "_ {*.state[2].stream[${stream_id}]}\n"
            def tree_file = new File("$gv_dir/${stream.name}.inf")
            def binding = [
                GAM : stream.gv.gam,
                STATSFILE: stats_file.toString(),
                QUESTIONS: questions,
                STREAMLINE: streamline,
                OUTPUT: tree_file.toString()
            ]

            def simple = new SimpleTemplateEngine()
            def source = script_template_file.text
            File script_file = new File("$hhed_script_dir/cxc_${stream.name}_gv.hed")
            script_file.text = simple.createTemplate(source).make(binding).toString()

            // 2. build the decision tree
            def params = []
            if (stream.gv.thr == "000") {
                params += ["-m", "-a", stream.gv.mdlf]
            }

            hts_wrapper.HHEdOnMMF(script_file.toString(),
                                  list_file.toString(),
                                  clustered_model_file.toString(),
                                  clustered_model_file.toString(),
                                  params)

            stream_id++;
        }
    }
}
