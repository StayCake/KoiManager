package com.koisv.dkm.data

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

object Convert {
    data class Time(
        var year: Int = 0, var month: Int = 0, var day: Int = 0,
        var hour: Int = 0, var min: Int = 0, var sec: Int = 0)

    private fun timeCalc(raw: Int) : Time {
        var calcT = raw
        val t = Time()
        while (calcT >= 31536000) {
            t.year += 1
            calcT -= 31536000
        }
        while (calcT >= 2592000) {
            t.month += 1
            calcT -= 2592000
        }
        while (calcT >= 86400) {
            t.day += 1
            calcT -= 86400
        }
        while (calcT >= 3600) {
            t.hour += 1
            calcT -= 3600
        }
        while (calcT >= 60) {
            t.min += 1
            calcT -= 60
        }
        t.sec = calcT
        return t
    }

    fun timeStamp(track:AudioTrack?) : String {
        if (track == null) return "시간 정보 없음"
        val native = (track.duration / 1000).toInt()
        val (_,month,day,hour,min,sec) = timeCalc(native)
        return if (month >= 1) "실시간" else {
            "${
                if (day != 0) "%02d:".format(day) else ""
            }${
                if (hour != 0) "%02d:".format(hour) else if (day != 0) "00:" else ""
            }${
                if (min != 0) "%02d:%02d".format(min,sec) else if (hour != 0) "00:%02d".format(min) else "00:%02d".format(sec)
            }"
        }
    }
    fun timeStamp(time: Int) : String {
        val (year,month,day,hour,min,sec) = timeCalc(time)
        return "${
            if (year != 0) "%d년 ".format(year) else ""
        }${
            if (month != 0) "%d달 ".format(month) else ""
        }${
            if (day != 0) "%d일 ".format(day) else ""
        }${
            if (hour != 0) "%d시간 ".format(hour) else ""
        }${
            if (min != 0) "%d분 ".format(min) else ""
        }${
            "%d초".format(sec)
        }"
    }
}
