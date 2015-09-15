/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.examples.scala.relational

//import org.apache.flink.api.java.DataSet
//import org.apache.flink.api.scala.DataSet

import org.apache.flink.api.java.DataSet
import org.apache.flink.api.java.DataSet
import org.apache.flink.api.scala.{ExecutionEnvironment, DataSet}
import org.apache.flink.ml.MLUtils
import org.apache.flink.ml.common.LabeledVector
import org.apache.flink.ml.math.DenseVector
import org.apache.flink.ml.regression.MultipleLinearRegression

import scala.io.Source

object CancerRegression {

  def main(args: Array[String]) {

    //val s: String = "bcr_patient_uuid\tbcr_patient_barcode\tform_completion_date\thistologic_diagnosis\tprospective_collection\tretrospective_collection\tgender\tsubmitted_tumor_site\trace\tethnicity\thistory_other_malignancy\tanatomic_organ_subdivision\tlocation_lung_parenchyma\thistologic_diagnosis\tinitial_pathologic_dx_year\tresidual_tumor\tajcc_staging_edition\tajcc_tumor_pathologic_pt\tajcc_nodes_pathologic_pn\tajcc_metastasis_pathologic_pm\tajcc_pathologic_tumor_stage\tajcc_tumor_clinical_ct\tajcc_nodes_clinical_cn\tajcc_metastasis_clinical_cm\tajcc_clinical_tumor_stage\tpulmonary_function_test_indicator\tfev1_percent_ref_prebroncholiator\tfev1_percent_ref_postbroncholiator\tfev1_fvc_ratio_prebroncholiator\tfev1_fvc_ratio_postbroncholiator\tcarbon_monoxide_diffusion_dlco\tkras_gene_analysis_indicator\tkras_mutation_found\tkras_mutation_identified_type\tegfr_mutation_status\tegfr_mutation_identified_type\teml4_alk_translocation_status\teml4_alk_analysis_type\ttobacco_smoking_history_indicator\thistory_neoadjuvant_treatment\ttobacco_smoking_year_started\ttobacco_\nsmoking_year_stopped\ttobacco_smoking_pack_years_smoked\tkarnofsky_score\tvital_status\tecog_score\tperformance_status_timing\tradiation_treatment_adjuvant\ttreatment_outcome_first_course\ttumor_status\tnew_tumor_event_dx_indicator\tbirth_days_to\tlast_contact_days_to\tdeath_days_to\tage_at_initial_pathologic_diagnosis\tanatomic_neoplasm_subdivision_other\tdays_to_initial_pathologic_diagnosis\tdisease_code\teml4_alk_translocation_variant\textranodal_involvement\ticd_10\ticd_o_3_histology\ticd_o_3_site\tinformed_consent_verified\tpatient_id\tproject_code\ttargeted_molecular_therapy\ttissue_source_site\nbcr_patient_uuid\tbcr_patient_barcode\tform_completion_date\tdiagnosis\ttissue_prospective_collection_indicator\ttissue_retrospective_collection_indicator\tgender\ttumor_tissue_site\trace\tethnicity\tprior_dx\tanatomic_neoplasm_subdivision\tlocation_in_lung_parenchyma\thistological_type\tyear_of_initial_pathologic_diagnosis\tresidual_tumor\tsystem_version\tpathologic_T\tpathologic_N\tpathologic_M\tpathologic_stage\tclinical_T\tclinical_N\tclinical_M\tclinical_stage\tpulmonary_function_test_performed\tpre_bronchodilator_fev1_percent\tpost_bronchodilator_fev1_percent\tpre_bronchodilator_fev1_fvc_percent\tpost_bronchodilator_fev1_fvc_percent\tdlco_predictive_percent\tkras_gene_analysis_performed\tkras_mutation_found\tkras_mutation_result\tegfr_mutation_performed\tegfr_mutation_result\teml4_alk_translocation_performed\teml4_alk_translocation_method\ttobacco_smoking_history\thistory_of_neoadjuvant_treatment\tyear_of_tobacco_smoking_onset\tstopped_smoking_year\tnumber_pack_years_smoked\tkarnofsky_performance_score\tvital_status\teastern_cancer_oncology_group\t\nperformance_status_scale_timing\tradiation_therapy\tprimary_therapy_outcome_success\tperson_neoplasm_cancer_status\tnew_tumor_event_after_initial_treatment\tdays_to_birth\tdays_to_last_followup\tdays_to_death\tage_at_initial_pathologic_diagnosis\tanatomic_neoplasm_subdivision_other\tdays_to_initial_pathologic_diagnosis\tdisease_code\teml4_alk_translocation_result\textranodal_involvement\ticd_10\ticd_o_3_histology\ticd_o_3_site\tinformed_consent_verified\tpatient_id\tproject_code\ttargeted_molecular_therapy\ttissue_source_site"
    //val samples: String = "patientid\tage\ttobacco_smoking_history_indicator\tperson_neoplasm_cancer_status"
    //val splitted = samples.split("\t")
    //val text =  scala.tools.nsc.io.File("/home/mi/nwulkow/ADL/Data/Data2/Clinical/Biotab/nationwidechildrens.org_clinical_patient_luad.txt").lines()


    //val text = Source.fromInputStream(getClass.getResourceAsStream("./sampledata.txt")).mkString //org_clinical_patient_luad
    val text = Source.fromFile("/home/mi/nwulkow/ADL/Data/sampledata.txt").getLines().toList
    val lines = text
    //println(lines(1))

    //val cancerstatus: String = "person_neoplasm_cancer_status"
    //Find the index of a parameter
    // -------------
    def getParameterIndex(parametername: String, topline: Array[String]): Double = {
      //val parametername = "bcr_patient_barcode"
      var parameterIndex: Double = 0
      for (a <- Range(0, topline.length)) {
        if (topline(a).equals(parametername)) {
          //println("pname="+parametername)
          parameterIndex = a
        }
      }
      parameterIndex
    }
    //---------------


    val params: Array[Double] = new Array[Double](3)
    val lvs: Array[LabeledVector] = new Array[LabeledVector](lines.length)
    params(0) = getParameterIndex("age", lines(0).split("\t"))
    params(1) = getParameterIndex("tobacco_smoking_history_indicator", lines(0).split("\t"))
    params(2) = getParameterIndex("healthynutrition", lines(0).split("\t"))
    val cancerstateIndex = getParameterIndex("person_neoplasm_cancer_status", lines(0).split("\t"))



    val env = ExecutionEnvironment.getExecutionEnvironment
    import org.apache.flink.api.scala.DataSet



    //var lvset: DataSet[LabeledVector] = null
    var i: Int = 0

    for (i <- Range(1, lines.length)) {
      var currentline = lines(i)
      val splittedline = currentline.split("\t")
      val cancerstate = splittedline(cancerstateIndex.toInt).toDouble
      val age = splittedline(params(0).toInt).toDouble
      val smoke = splittedline(params(1).toInt).toDouble
      val loc = splittedline(params(2).toInt).toDouble
      val vector = DenseVector(age, smoke, loc)
      val lv = LabeledVector(cancerstate, vector)
      lvs(i - 1) = lv
      //i = i + 1
    }
   // println(lvs(1))

//    lvs.map(c => LabeledVector(c.label, c.vector))

    // Create a LabeledVector-DataSet with the LabeledVectors in lvs
    // Then do regression on that DataSet

    //-------------------
    import java.io._
    val outputpath : String = "/home/mi/nwulkow/ADL/Data/outputwrite1"
    val pw = new PrintWriter(new File(outputpath))

    for (i <- Range(0, lvs.length-1)) {
      //println(lvs(i).label)
      pw.write(lvs(i).label.toInt + " ")
      for (j <- Range(1,lvs(i).vector.size)){
        pw.write(j + ":" + lvs(i).vector(j) + " ")
      }
      pw.write("\n")

    }
    pw.close


    import org.apache.flink.api.scala.DataSet

    val lvdataset: DataSet[LabeledVector] = MLUtils.readLibSVM(env ,outputpath)

    //------------------------


    val mlr = MultipleLinearRegression()
      .setStepsize(1.0)
      .setIterations(100)
      .setConvergenceThreshold(0.001)
    mlr.fit(lvdataset)
    val weights = mlr.weightsOption
    println(weights.get)
    //println(weights.) //writeAsCsv("/home/mi/nwulkow/ADL/Data/weights", "\n", ",")


    //val d = DataSet[Double]
    //mlr.fit(d)
    //print(mlr.squaredResidualSum(d))

  }
}
