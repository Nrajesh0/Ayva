package com.focusbyrj.app.data

data class VocabStats(
    val totalLearned: Int,
    val totalMastered: Int,
    val learnedIdioms: Int,
    val learnedOws: Int,
    val masteredIdioms: Int,
    val masteredOws: Int,
    val pendingReview: Int
)

class VocabRepository(val vocabDao: VocabDao) {
    suspend fun getNextIdiomToLearn(): Idiom? {
        return vocabDao.getUnlearnedIdioms(1).firstOrNull()
    }

    suspend fun getNextOwsToLearn(): Ows? {
        return vocabDao.getUnlearnedOws(1).firstOrNull()
    }

    suspend fun markIdiomLearned(idiom: Idiom) {
        val id = idiom.id ?: return
        markIdiomLearned(id)
    }

    suspend fun markOwsLearned(ows: Ows) {
        val id = ows.id ?: return
        markOwsLearned(id)
    }

    suspend fun markIdiomLearned(id: Int) {
        vocabDao.setIdiomLearned(id, System.currentTimeMillis())
    }

    suspend fun markOwsLearned(id: Int) {
        vocabDao.setOwsLearned(id, System.currentTimeMillis())
    }

    // Spaced repetition review candidate selection (Leitner queue: oldest unreviewed word)
    suspend fun getRevisionIdiom(excludeId: Int? = null): Idiom? {
        val unmastered = vocabDao.getRevisionIdioms(1, excludeId).firstOrNull()
        if (unmastered != null) return unmastered
        return vocabDao.getAllRevisionIdiomsFallback(1, excludeId).firstOrNull()
    }

    suspend fun getRevisionOws(excludeId: Int? = null): Ows? {
        val unmastered = vocabDao.getRevisionOws(1, excludeId).firstOrNull()
        if (unmastered != null) return unmastered
        return vocabDao.getAllRevisionOwsFallback(1, excludeId).firstOrNull()
    }

    // When revision word is viewed, update review timestamp to cycle it to back of queue
    suspend fun touchRevision(idiomId: Int?, owsId: Int?) {
        val now = System.currentTimeMillis()
        if (idiomId != null) vocabDao.setIdiomLearned(idiomId, now)
        if (owsId != null) vocabDao.setOwsLearned(owsId, now)
    }

    // Quiz feedback loop: updates mastery and sets review priority
    suspend fun recordQuizResult(type: String, id: Int, isCorrect: Boolean) {
        val now = System.currentTimeMillis()
        if (isCorrect) {
            // Mastered on quiz success, refresh review timestamp
            if (type == "idiom") {
                vocabDao.setIdiomMastery(id, 1, now)
            } else {
                vocabDao.setOwsMastery(id, 1, now)
            }
        } else {
            // Mistake demotes mastery and sets timestamp to 1 so it's top priority for next morning revision!
            if (type == "idiom") {
                vocabDao.setIdiomMastery(id, 0, 1L)
            } else {
                vocabDao.setOwsMastery(id, 0, 1L)
            }
        }
    }

    suspend fun getQuizWords(): Pair<List<Idiom>, List<Ows>> {
        val unmasteredIdioms = vocabDao.getUnmasteredLearnedIdioms(7)
        val neededIdioms = 10 - unmasteredIdioms.size
        val masteredIdioms = if (neededIdioms > 0) vocabDao.getMasteredLearnedIdioms(neededIdioms) else emptyList()
        val idioms = (unmasteredIdioms + masteredIdioms).shuffled()

        val unmasteredOws = vocabDao.getUnmasteredLearnedOws(7)
        val neededOws = 10 - unmasteredOws.size
        val masteredOws = if (neededOws > 0) vocabDao.getMasteredLearnedOws(neededOws) else emptyList()
        val owsList = (unmasteredOws + masteredOws).shuffled()

        return Pair(idioms, owsList)
    }

    suspend fun getStats(): VocabStats {
        val learnedIdioms = vocabDao.getLearnedIdiomCount()
        val learnedOws = vocabDao.getLearnedOwsCount()
        val masteredIdioms = vocabDao.getMasteredIdiomCount()
        val masteredOws = vocabDao.getMasteredOwsCount()
        val totalLearned = learnedIdioms + learnedOws
        val totalMastered = masteredIdioms + masteredOws
        return VocabStats(
            totalLearned = totalLearned,
            totalMastered = totalMastered,
            learnedIdioms = learnedIdioms,
            learnedOws = learnedOws,
            masteredIdioms = masteredIdioms,
            masteredOws = masteredOws,
            pendingReview = (totalLearned - totalMastered).coerceAtLeast(0)
        )
    }

    suspend fun getAllLearnedIdioms(): List<Idiom> {
        return vocabDao.getAllLearnedIdioms()
    }

    suspend fun getAllLearnedOws(): List<Ows> {
        return vocabDao.getAllLearnedOws()
    }

    suspend fun getLastLearnedIdiom(): Idiom? {
        return vocabDao.getLastLearnedIdiom()
    }

    suspend fun getLastLearnedOws(): Ows? {
        return vocabDao.getLastLearnedOws()
    }
}
