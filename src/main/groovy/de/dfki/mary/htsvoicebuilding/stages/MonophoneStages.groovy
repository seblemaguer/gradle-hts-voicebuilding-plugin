package de.dfki.mary.htsvoicebuilding.stages

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

import static groovyx.gpars.GParsPool.runForkJoin
import static groovyx.gpars.GParsPool.withPool

import de.dfki.mary.htsvoicebuilding.DataFileFinder

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.xml.*

class MonophoneStages {

    /****************************************************************************************
     ** 
     ****************************************************************************************/
    public static void addTasks(Project project)
    {
        project.task('initialiseMonophoneModels', dependsOn: 'initModels')
        {
            // FIXME: use the list ! 
            def monophone_set = new HashSet()
            (new File(DataFileFinder.getFilePath(project.user_configuration.data.scp))).eachLine{ cur_file ->
                def basename = (new File(cur_file)).name.replace(".cmp", "")
            
                // Analyse file
                (new File(DataFileFinder.getFilePath(project.user_configuration.data.mono_lab_dir + "/" + basename + ".lab"))).eachLine { cur_lab ->
                    def line_arr = cur_lab =~ /^[ \t]*([0-9]+)[ \t]+([0-9]+)[ \t]+(.+)/
                    monophone_set.add(line_arr[0][3])        
                }
            }
            
            inputs.files project.mono_mlf_filename,  "$project.cmp_model_dir/average.mmf", \
            "$project.cmp_model_dir/init.mmf", "$project.dur_model_dir/average.mmf"
            monophone_set.each { phone ->
                outputs.files "$project.cmp_model_dir/HInit/$phone", "$project.cmp_model_dir/HRest/$phone"
            }
            // Generate project
            doLast {
                withPool(project.nb_proc) {
                    monophone_set.eachParallel { phone ->
                        // inputs.files project.mono_mlf_filename,  "$project.cmp_model_dir/average.mmf", \
                        // "$project.cmp_model_dir/init.mmf", "$project.dur_model_dir/average.mmf"
                        // outputs.files "$project.cmp_model_dir/HInit/$phone", "$project.cmp_model_dir/HRest/$phone"
                    
                        if (project.user_configuration.settings.daem.use) {
                            println("use average model instead of $phone")
                        
                            // CMP
                            def contents = (new File("$project.cmp_model_dir/average.mmf")).text
                            contents = contents.replaceAll("average.mmf", "$phone")
                            (new File("$project.cmp_model_dir/HRest/$phone")).write(contents)
                        
                            // Duration
                            contents = (new File("$project.dur_model_dir/average.mmf")).text
                            contents = contents.replaceAll("average.mmf", "$phone")
                            (new File("$project.dur_model_dir/HRest/$phone")).write(contents)
                        
                        } else {
                        
                            /**
                             * FIXME: See for  
                             *   - WARNING [-7032]  OWarn: change HMM Set swidth[0] in HRest
                             *   - WARNING [-7032]  OWarn: change HMM Set msdflag[0] in HRest
                             */
                            // HInit
                            project.hts_wrapper.HInit(phone, DataFileFinder.getFilePath(project.user_configuration.data.scp),
                                                      "$project.proto_dir/proto", "$project.cmp_model_dir/init.mmf",
                                                      project.mono_mlf_filename, "$project.cmp_model_dir/HInit")
                        
                        
                            // HInit
                            project.hts_wrapper.HRest(phone, DataFileFinder.getFilePath(project.user_configuration.data.scp),
                                                      "$project.cmp_model_dir/HInit", "$project.cmp_model_dir/init.mmf",
                                                      project.mono_mlf_filename,
                                                      "$project.cmp_model_dir/HRest", "$project.dur_model_dir/HRest")
                        }
                    }
                }
            }
}

        project.task('generateMonophoneMMF', dependsOn: 'initialiseMonophoneModels')
        {
            // outputs.upToDateWhen { 
            //     false 
            // } 

            // FIXME: use the list ! 
            def monophone_set = new HashSet()
            (new File(DataFileFinder.getFilePath(project.user_configuration.data.scp))).eachLine{ cur_file ->
                def basename = (new File(cur_file)).name.replace(".cmp", "")
            
                // Analyse file
                (new File(DataFileFinder.getFilePath(project.user_configuration.data.mono_lab_dir + "/" + basename + ".lab"))).eachLine { cur_lab ->
                    def line_arr = cur_lab =~ /^[ \t]*([0-9]+)[ \t]+([0-9]+)[ \t]+(.+)/
                    monophone_set.add(line_arr[0][3])        
                }
            }
            
            monophone_set.each { phone ->
                inputs.files "$project.cmp_model_dir/HInit/$phone", "$project.cmp_model_dir/HRest/$phone"
            }
            
            outputs.files project.cmp_model_dir + "/monophone.mmf.noembedded.gz", project.dur_model_dir + "/monophone.mmf.noembedded.gz", project.cmp_model_dir + "/monophone.mmf",project.cmp_model_dir + "/monophone.mmf"
        
            // Generate HHEd script
            project.copy {
                from project.template_dir
                into project.hhed_script_dir
            
                include 'lvf.hed'
                rename { file -> "lvf.cmp.hed"}
                def binding = [
                    STARTSTATE:2,
                               ENDSTATE:project.user_configuration.models.global.nb_emitting_states+1,
                               VFLOORFILE:project.cmp_model_dir + "/vFloors",
                               NB_STREAMS: project.user_configuration.models.cmp.streams.size()
                ]
            
                expand(binding)
            }
        
        
            // Generate HHEd script
            def content = "// Load variance flooring macro\n"
            content += "FV \"" + project.dur_model_dir + "/vFloors\""
            (new File(project.hhed_script_dir + "/lvf.dur.hed")).write(content)
        
        
            doLast {
                project.hts_wrapper.HHEdOnDir(project.hhed_script_dir + "/lvf.cmp.hed", project.mono_list_filename,
                                              project.cmp_model_dir + "/HRest", project.cmp_model_dir + "/monophone.mmf")
            
            
                project.hts_wrapper.HHEdOnDir(project.hhed_script_dir + "/lvf.dur.hed", project.mono_list_filename,
                                              project.dur_model_dir + "/HRest", project.dur_model_dir + "/monophone.mmf")
            
                // // Save a gzip copy
                // exec {
                //     def bash_cmd = ["gzip", "-c", project.cmpModelDirectory + "/monophone.mmf", ">", project.cmpModelDirectory + "/monophone.mmf.noembedded.gz"]
                //     commandLine("bash", "-c", bash_cmd.join(" "))
                // }
            
                // // Save a gzip copy
                // exec {
                //     def bash_cmd = ["gzip", "-c", project.durModelDirectory + "/monophone.mmf", ">", project.durModelDirectory + "/monophone.mmf.noembedded.gz"]
                //     commandLine("bash", "-c", bash_cmd.join(" "))
                // }
            }
}
    
        project.task('trainMonophoneMMF', dependsOn: 'generateMonophoneMMF')
        {
            // outputs.upToDateWhen { 
            //     false 
            // } 

            // FIXME: inputs
            outputs.files project.cmp_model_dir + "/monophone.mmf.embedded.gz", project.dur_model_dir + "/monophone.mmf.embedded.gz", project.cmp_model_dir + "/monophone.mmf",project.cmp_model_dir + "/monophone.mmf"
        
            doLast {
                if (project.user_configuration.settings.daem.use) {
                    for (i in 1..project.user_configuration.settings.daem.nIte) {
                        for (j in 1..project.user_configuration.settings.training.nIte) {
                            // 
                            def k = j + (i-1) ** project.user_configuration.settings.training.nIte
                            println("\n\nIteration $k of Embedded Re-estimation")
                        
                            k = (i / project.user_configuration.settings.daem.nIte) ** project.user_configuration.settings.daem.alpha
                            
                            project.hts_wrapper.HERest(DataFileFinder.getFilePath(project.user_configuration.data.scp),
                                                       project.mono_list_filename,
                                                       project.mono_mlf_filename,
                                                       project.cmp_model_dir + "/monophone.mmf",
                                                       project.dur_model_dir + "/monophone.mmf",
                                                       project.cmp_model_dir,
                                                       project.dur_model_dir
                                                       ["-k", k])
                        }
                    }
                } else {
                    for (i in 1..project.user_configuration.settings.training.nIte) {
                        // 
                        println("\n\nIteration $i of Embedded Re-estimation")
                    
                        project.hts_wrapper.HERest(DataFileFinder.getFilePath(project.user_configuration.data.scp),
                                                   project.mono_list_filename,
                                                   project.mono_mlf_filename,
                                                   project.cmp_model_dir + "/monophone.mmf",
                                                   project.dur_model_dir + "/monophone.mmf",
                                                   project.cmp_model_dir,
                                                   project.dur_model_dir,
                                                   [])
                    }
                }
            
                /*
                exec {
                    def bash_cmd = ["gzip", "-c", project.cmpModelDirectory + "/monophone.mmf", ">", project.cmpModelDirectory + "/monophone.mmf.embedded.gz"]
                    commandLine("bash", "-c", bash_cmd.join(" "))
                }
            
            
                exec {
                    def bash_cmd = ["gzip", "-c", project.durModelDirectory + "/monophone.mmf", ">", project.durModelDirectory + "/monophone.mmf.embedded.gz"]
                    commandLine("bash", "-c", bash_cmd.join(" "))
                }
                */
            }
        }
    }
}
