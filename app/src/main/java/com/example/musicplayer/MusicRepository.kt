package com.example.musicplayer

class MusicRepository {

    private var musicList = mutableListOf<MusicClass>()
    private var currentSong = 0

    fun initPlayList() {
        for ((index, music) in SONGS_URI_LIST.withIndex())
            musicList.add(
                index,
                MusicClass(
                    music,
                    SONGS_NAMES_LIST[index],
                    SONGS_AUTHORS_LIST[index]
                )
            )
    }

    fun getCurrentSong(): MusicClass {
        return musicList[currentSong]
    }

    fun getNextSong(): MusicClass {
        if (++currentSong == SONGS_URI_LIST.size)
            currentSong = 0
        return musicList[currentSong]
    }

    fun getPreviousSong(): MusicClass {
        if (--currentSong < 0)
            currentSong = SONGS_URI_LIST.size - 1
        return musicList[currentSong]
    }

    companion object {
        private val SONGS_URI_LIST = listOf(
            R.raw.music1, R.raw.music2, R.raw.music3,
            R.raw.music4, R.raw.music5, R.raw.music6
        )
        private val SONGS_NAMES_LIST = listOf(
            "Cola", "Fuck Them", "Haunted House",
            "Timmy Turner", "Its on me", "Internal"
        )
        private val SONGS_AUTHORS_LIST = listOf(
            "CamelPhat, ElderBrook", "Libercio - Loose Screw", "Aarne, Big Baby Tape, Kizaru",
            "McEn", "Mr. Shamrock feat. Stevie Stone", "Rogue Dave"
        )
    }
}