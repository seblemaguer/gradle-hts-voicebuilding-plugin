package de.dfki.mary.htsvoicebuilding.stages.task.config

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
 *  Task to generate the file containing the list of labels
 *
 */
public class GenerateListTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** SCP File */
    @InputFile
    final RegularFileProperty scp_file = newInputFile()

    /** Directory containing the labels */
    @InputDirectory
    final DirectoryProperty lab_dir = newInputDirectory()

    /** List file produced by the worker */
    @OutputFile
    final RegularFileProperty list_file = newOutputFile()


    /**
     *  The constructor which defines which worker executor is going to achieve the generation job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateListTask(WorkerExecutor workerExecutor) {
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
        workerExecutor.submit(GenerateListWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(scp_file.getAsFile().get(),
                                  lab_dir.getAsFile().get(),
                                  list_file.getAsFile().get());
                }
            });
    }
}


/**
 *  Worker class to generate the file containing the list of labels
 *
 */
class GenerateListWorker implements Runnable {

    /** SCP file */
    private File scp_file;

    /** Directory containing the labels */
    private File lab_dir;

    /** List file produced by the worker */
    private File list_file;

    /**
     *  The constructor of the worker
     *
     *  @param scp_file file containing the basename list
     *  @param lab_dir the directory containing the labels
     *  @param list_file the List file generated
     */
    @Inject
    public GenerateListWorker(File scp_file, File lab_dir, File list_file) {
        this.scp_file = scp_file;
        this.lab_dir = lab_dir;
        this.list_file = list_file;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        // Generate set of labels
        def model_set = new HashSet();
        for (String input_filename: scp_file.readLines()) {
            String basename = (new File(input_filename)).getName() - ".cmp"
            (new File(lab_dir.toString(),  basename + ".lab")).eachLine { line ->
                def line_arr = line =~ /^[ \t]*([0-9]+)[ \t]+([0-9]+)[ \t]+(.+)/
                if (line_arr.size() == 0) {
                    model_set.add(line)
                } else {
                    model_set.add(line_arr[0][3])
                }
            }
        }

        // Save it into the list
        list_file.text = model_set.join("\n")
    }
}
