package com.example.watchckpool

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.watchckpool.WatchUtils.formatHashrate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class BackgroundWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = MonitorRepository(applicationContext)
        val settings = repo.settings.first()

        if (settings.btcAddress.isEmpty()) return Result.success()

        val fullUrl = "${settings.selectedUrl}${settings.btcAddress}"

        return try {
            val connection = withContext(Dispatchers.IO) {
                URL(fullUrl).openConnection()
            } as HttpURLConnection
            connection.connectTimeout = 10000
            val code = connection.responseCode

            if (code == 200) {
                val newJson = connection.inputStream.bufferedReader().use { it.readText() }
                val lastJson = repo.lastData.first()

                compareAndLog(repo, settings, lastJson, newJson)
                repo.saveResponse(newJson)
                repo.addLog("Background check successful")
                Result.success()
            } else {
                repo.addLog("Background check failed: Server returned $code")
                Result.retry()
            }
        } catch (e: Exception) {
            repo.addLog("Background check error: ${e.localizedMessage}")
            Result.retry()
        }
    }

    private suspend fun compareAndLog(
        repo: MonitorRepository, settings: AppSettings, oldStr: String?, newStr: String
    ) {
        if (oldStr == null) return

        try {
            val old = Json.parseToJsonElement(oldStr).jsonObject
            val current = Json.parseToJsonElement(newStr).jsonObject

            val shortAddress =
                if (settings.btcAddress.length > 12) "${settings.btcAddress.take(5)}...${
                    settings.btcAddress.takeLast(4)
                }"
                else settings.btcAddress

            // 1. Miner "workers" added diff
            val oldWCount = old["workers"]?.jsonPrimitive?.intOrNull ?: 0
            val newWCount = current["workers"]?.jsonPrimitive?.intOrNull ?: 0
            if (oldWCount != newWCount) {
                val diff = newWCount - oldWCount


                val msgLog = "minerlog|$shortAddress|workers|$oldWCount|$newWCount"

                val initMsgNotif = if (diff > 0) "👍 More Workers: +" else "👎 Less Workers: "
                val msgNotif = "$initMsgNotif$diff ($oldWCount -> $newWCount)"

                if (settings.logMinerWorkers) repo.appendPhysicalLog("miner_logs.txt", msgLog)
                if (settings.notifyMinerWorkers) sendNotification("⛏️ $shortAddress", msgNotif, "dashboard")
            }

            // 2. Miner "bestshare"
            val oldBestShare = old["bestshare"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val newBestShare = current["bestshare"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            if (newBestShare > oldBestShare) {
                val msgLog = "minerlog|$shortAddress|bestshare|$oldBestShare|$newBestShare"
                if (settings.logMinerBestShare) repo.appendPhysicalLog("miner_logs.txt", msgLog)
                if (oldBestShare > 1) { //  if we have a previous value to compare
                    val percent = WatchUtils.calculatePercentChange(oldBestShare, newBestShare)


                    val msgNotif = "👍 BestShare $percent ${formatHashrate(newBestShare)}"
                    if (settings.notifyMinerBestShare) sendNotification(
                        "⛏️ $shortAddress", msgNotif, "dashboard"
                    )
                } else {

                    val msgNotif = "👍 New Best Share: $newBestShare"
                    if (settings.notifyMinerBestShare) sendNotification(
                        "⛏️ $shortAddress", msgNotif, "dashboard"
                    )
                }
            }

            // 3. Miner "bestever"
            val oldBestEver = old["bestever"]?.jsonPrimitive?.longOrNull ?: 0L
            val newBestEver = current["bestever"]?.jsonPrimitive?.longOrNull ?: 0L
            if (newBestEver > oldBestEver) {
                val msgLog = "minerlog|$shortAddress|bestever|$oldBestEver|$newBestEver"
                if (settings.logMinerBestEver) repo.appendPhysicalLog("miner_logs.txt", msgLog)
                if (oldBestEver > 1) { // Only calculate percent if we have a previous value to compare
                    val percent = WatchUtils.calculatePercentChange(
                        oldBestEver.toDouble(), newBestEver.toDouble()
                    )

                    // val msgNotif = "New Best Ever $percent newBestEver"
                    val msgNotif = "👍 BestEver $percent ${formatHashrate(newBestEver.toDouble())}"
                    if (settings.notifyMinerBestEver) sendNotification("⛏️ $shortAddress", msgNotif, "dashboard")
                } else {

                    val msgNotif = "👍 New Best Ever: $newBestEver"
                    if (settings.notifyMinerBestEver) sendNotification("⛏️ $shortAddress", msgNotif, "dashboard")
                }
            }

            // 4. Workers Array Comparison
            val oldWorkers = old["worker"]?.jsonArray?.associateBy {
                it.jsonObject["workername"]?.jsonPrimitive?.content ?: ""
            } ?: emptyMap()
            val newWorkers = current["worker"]?.jsonArray?.associateBy {
                it.jsonObject["workername"]?.jsonPrimitive?.content ?: ""
            } ?: emptyMap()

            newWorkers.forEach { (name, worker) ->
                val oldWorker = oldWorkers[name]?.jsonObject
                val currentWorker = worker.jsonObject
                val cleanWorkerName = WatchUtils.cleanWorkerName(name)

                //   val cleanName = if (cleanWorkerName.length > 12) "${cleanWorkerName.take(6)}...${cleanWorkerName.takeLast(6)}" else cleanWorkerName
                //   val shortName = cleanWorkerName.substring(0,10)
                val shortName = if (cleanWorkerName.length > 12) "${cleanWorkerName.take(5)}...${
                    cleanWorkerName.takeLast(4)
                }"
                else cleanWorkerName


                // Worker Best Share
                val oldWBestShare = oldWorker?.get("bestshare")?.jsonPrimitive?.doubleOrNull ?: 0.0
                val newWBestShare = currentWorker["bestshare"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                if (newWBestShare > oldWBestShare) {
                    val msgLog = "workerslog|$shortName|bestshare|$oldWBestShare|$newWBestShare"
                    if (settings.logWorkerBestShare) repo.appendPhysicalLog(
                        "workers_logs.txt", msgLog
                    )
                    if (oldWBestShare > 1) { // Only calculate percent if we have a previous value to compare
                        val percent =
                            WatchUtils.calculatePercentChange(oldWBestShare, newWBestShare)

                        //     val msg = "Worker $cleanName: New Best Share $newWBestShare"

                        //  val msgNotif = "New Best Share: $newWBestShare"
                        val msgNotif = "BestShare $percent ${formatHashrate(newWBestShare)}"

                        if (settings.notifyWorkerBestShare) sendNotification(
                            "🛠️ $shortName", msgNotif, "workers"
                        )
                    } else {

                        //    val msgNotif = "New Best Share: $newWBestShare"
                        val msgNotif = "BestShare ${formatHashrate(newWBestShare)}"

                        if (settings.notifyWorkerBestShare) sendNotification(
                            "🛠️ $shortName", msgNotif, "workers"
                        )

                    }


                }

                // Worker Best Ever
                val oldWBestEver = oldWorker?.get("bestever")?.jsonPrimitive?.longOrNull ?: 0L
                val newWBestEver = currentWorker["bestever"]?.jsonPrimitive?.longOrNull ?: 0L
                if (newWBestEver > oldWBestEver) {
                    val msgLog = "workerslog|$shortName|bestever|$oldWBestEver|$newWBestEver"
                    if (settings.logWorkerBestEver) repo.appendPhysicalLog(
                        "workers_logs.txt", msgLog
                    )
                    if (oldWBestEver > 0) { // Only log if we have a previous value to compare
                        val percent = WatchUtils.calculatePercentChange(
                            oldWBestEver.toDouble(), newWBestEver.toDouble()
                        )


                        //       val msg = "Worker $cleanName: New Best Ever $newWBestEver"

                        // val msgNotif = "New Best Ever $percent $newWBestEver"
                        val msgNotif =
                            "BestEver $percent ${formatHashrate(newWBestEver.toDouble())}"

                        if (settings.notifyWorkerBestEver) sendNotification(
                            "🛠️ $shortName", msgNotif, "workers"
                        )
                    } else {

                        //   val msgNotif = "New Best Ever: $newWBestEver"
                        val msgNotif = "BestEver ${formatHashrate(newWBestEver.toDouble())}"

                        if (settings.notifyWorkerBestEver) sendNotification(
                            "🛠️ $shortName", msgNotif, "workers"
                        )

                    }
                }

                // Worker Hashrate 1m Monitoring
                val oldHashrate = oldWorker?.get("hashrate1m")?.jsonPrimitive?.content ?: "0H"
                val newHashrate = currentWorker["hashrate1m"]?.jsonPrimitive?.content ?: "0H"
                if (oldHashrate != newHashrate) {
                    val oldVal = WatchUtils.parseHashrate(oldHashrate)
                    val newVal = WatchUtils.parseHashrate(newHashrate)
                    if (oldVal > 0) { // Only log if we have a previous value to compare
                        val percent = WatchUtils.calculatePercentChange(oldVal, newVal)
                        //   val msg = "Worker $cleanName: Hashrate changed $oldHashrate -> $newHashrate ($percent)"
                        val msgLog = "workerslog|$shortName|hashrate1m|$oldHashrate|$newHashrate"
                        val msgNotif = "hashrate1m $percent $oldHashrate -> $newHashrate "

                        if (settings.logWorkerHashrate) repo.appendPhysicalLog(
                            "workers_logs.txt", msgLog
                        )
                        if (settings.notifyWorkerHashrate) sendNotification(
                            "🛠️ $shortName", msgNotif, "workers"
                        )
                    }
                }
            }

        } catch (e: Exception) {
            repo.addLog("Comparison failed: Invalid JSON structure")
        }
    }

    private fun sendNotification(title: String, msg: String, route: String) {
        val nm =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "miner_alerts"

        val channel =
            NotificationChannel(channelId, "Miner Alerts", NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannel(channel)

        // Create Intent with deep link URI
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("watchckpool://navigate/$route"),
            applicationContext,
            MainActivity::class.java
        )

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.wckp_ico_06_100mon)
            .setContentTitle(title)
            .setContentText(msg)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        fun start(ctx: Context, intervalMinutes: Int = 15) {
            val req =
                PeriodicWorkRequestBuilder<BackgroundWorker>(
                    intervalMinutes.toLong(),
                    TimeUnit.MINUTES
                ).setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                ).build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("Monitor", ExistingPeriodicWorkPolicy.REPLACE, req)
        }
    }
}
