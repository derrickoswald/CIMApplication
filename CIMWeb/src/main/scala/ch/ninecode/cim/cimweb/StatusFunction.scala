package ch.ninecode.cim.cimweb

import javax.json.Json
import javax.json.JsonArrayBuilder
import javax.json.JsonStructure
import org.apache.spark.JobExecutionStatus
import org.apache.spark.SparkJobInfo
import org.apache.spark.SparkStatusTracker
import org.apache.spark.sql.SparkSession

case class StatusFunction (group: String = "") extends CIMWebFunction
{
    def getStages (job: SparkJobInfo, tracker: SparkStatusTracker): JsonArrayBuilder =
    {
        val stages = Json.createArrayBuilder
        for (stage <- job.stageIds)
        {
            tracker.getStageInfo(stage) match
            {
                case Some (stageinfo) =>
                    val tasks = Json.createObjectBuilder
                        .add ("total", stageinfo.numTasks)
                        .add ("active", stageinfo.numActiveTasks)
                        .add ("completed", stageinfo.numCompletedTasks)
                        .add ("failed", stageinfo.numFailedTasks)
                    val st = Json.createObjectBuilder
                        .add ("name", stageinfo.name)
                        .add ("id", stageinfo.stageId)
                        .add ("time", stageinfo.submissionTime)
                        .add ("attempt", stageinfo.currentAttemptId)
                        .add ("tasks", tasks)
                    stages.add (st)
                case None =>
            }
        }
        stages
    }

    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        // form the response
        val tracker = spark.sparkContext.statusTracker
        val jobids = if ("" != group) tracker.getJobIdsForGroup(group) else tracker.getActiveJobIds
        val jobs = Json.createArrayBuilder
        for (job <- jobids) // assume that for long running sessions this is only jobs for the current function
        {
            tracker.getJobInfo(job) match
            {
                case Some (jobinfo) =>

                    val status = jobinfo.status
                    val obj = Json.createObjectBuilder
                        .add ("id", jobinfo.jobId)
                        .add ("status", status.name)
                    status match
                    {
                        case JobExecutionStatus.RUNNING =>
                            val _ = obj.add ("stages", getStages (jobinfo, tracker))
                        case JobExecutionStatus.SUCCEEDED =>
                        case JobExecutionStatus.FAILED =>
                        case JobExecutionStatus.UNKNOWN =>
                    }
                    val _ = jobs.add (obj)
                case None =>
            }
        }
        jobs.build
    }

    override def toString: String = s"${super.toString}"
}
