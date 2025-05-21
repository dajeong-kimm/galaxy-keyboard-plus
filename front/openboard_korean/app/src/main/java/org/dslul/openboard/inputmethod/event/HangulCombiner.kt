package org.dslul.openboard.inputmethod.event

import android.util.Log
import org.dslul.openboard.inputmethod.latin.common.Constants
import java.lang.StringBuilder
import java.util.ArrayList
import org.greenrobot.eventbus.EventBus
import org.dslul.openboard.inputmethod.event.HangulCommitEvent


class HangulCombiner : Combiner {

    val history: MutableList<HangulSyllable> = mutableListOf()
    val syllable: HangulSyllable? get() = history.lastOrNull()

    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event?): Event? {
        Log.d("HangulCombiner", "ENTER  processEvent: mKeyCode=${event?.mKeyCode} " +
                "mCodePoint=${event?.mCodePoint} " +
                "history=$history")
        if(event == null || event.mKeyCode == Constants.CODE_SHIFT) {
            Log.d("HangulCombiner", "EARLY RETURN: keyCode==CODE_SHIFT, skipping composition")
            return event
        }
        if (Character.isWhitespace(event.mCodePoint)) {
            // ① 현재 음절만 커밋
            val syllableText = syllable?.string ?: ""
            EventBus.getDefault()
                .post(HangulCommitEvent(HangulCommitEvent.TYPE_END, syllableText))

            // ② 음절 상태만 초기화
            history.clear()

            // ③ 공백키(스페이스) 이벤트는 원래대로 전달
            return Event.createHardwareKeypressEvent(
                event.mCodePoint,       // 공백 코드포인트
                event.mKeyCode,         // Constants.CODE_SPACE
                null,
                event.isKeyRepeat
            )
        }else if(event.isFunctionalKeyEvent) {
            if(event.mKeyCode == Constants.CODE_DELETE) {
                // ① 현재 음절만 커밋 취소
                val syllableText = syllable?.string ?: ""
                EventBus.getDefault()
                    .post(HangulCommitEvent(HangulCommitEvent.TYPE_END, syllableText))

                // ② state 초기화 (다음 음절부터 새로 조합)
                reset()

                // ③ 실제 삭제 이벤트 전달
                return Event.createHardwareKeypressEvent(
                    Event.NOT_A_CODE_POINT,   // codePoint 없이
                    Constants.CODE_DELETE,    // delete 키코드
                    null,
                    event.isKeyRepeat
                )
            }
            val text = combiningStateFeedback
            reset()
            return createEventChainFromSequence(text, event)
        } else {
            val currentSyllable = syllable ?: HangulSyllable()
            val jamo = HangulJamo.of(event.mCodePoint)

//            if(!event.isCombining || jamo is HangulJamo.NonHangul) {
            if (jamo is HangulJamo.NonHangul) {
                // 이전 음절 커밋
                val prev = combiningStateFeedback.toString()
                EventBus.getDefault().post(HangulCommitEvent(HangulCommitEvent.TYPE_SYLLABLE, prev))

                // NonHangul 문자도 함께 커밋
                EventBus.getDefault().post(HangulCommitEvent(HangulCommitEvent.TYPE_SYLLABLE, jamo.string))

                // 상태 초기화
                history.clear()

                // 두 음절(이전 + NonHangul) 한번에 내보내기
                return createEventChainFromSequence(prev + jamo.string, event)
            } else {
                when(jamo) {
                    is HangulJamo.Consonant -> {
                        val initial = jamo.toInitial()
                        val final = jamo.toFinal()
                        if(currentSyllable.initial != null && currentSyllable.medial != null) {
                            if(currentSyllable.final == null) {
                                val combination = COMBINATION_TABLE_DUBEOLSIK[currentSyllable.initial.codePoint to (initial?.codePoint ?: -1)]
                                if(combination != null) {
                                    history += currentSyllable.copy(initial = HangulJamo.Initial(combination))
                                } else {
                                    if(final != null) history += currentSyllable.copy(final = final)
                                    else {
                                        composingWord.append(currentSyllable.string)
                                        history.clear()
                                        history += HangulSyllable(initial = initial)
                                    }
                                }
                            } else {
                                val pair = currentSyllable.final.codePoint to (final?.codePoint ?: -1)
                                val combination = COMBINATION_TABLE_DUBEOLSIK[pair]
                                if(combination != null) {
                                    history += currentSyllable.copy(final = HangulJamo.Final(combination, combinationPair = pair))
                                } else {
                                    composingWord.append(currentSyllable.string)
                                    history.clear()
                                    history += HangulSyllable(initial = initial)
                                }
                            }
                        } else {
                            composingWord.append(currentSyllable.string)
                            history.clear()
                            history += HangulSyllable(initial = initial)
                        }
                    }
                    is HangulJamo.Vowel -> {
                        val medial = jamo.toMedial()
                        if(currentSyllable.final == null) {
                            if(currentSyllable.medial != null) {
                                val combination = COMBINATION_TABLE_DUBEOLSIK[currentSyllable.medial.codePoint to (medial?.codePoint ?: -1)]
                                if(combination != null) {
                                    history += currentSyllable.copy(medial = HangulJamo.Medial(combination))
                                } else {
                                    composingWord.append(currentSyllable.string)
                                    history.clear()
                                    history += HangulSyllable(medial = medial)
                                }
                            } else {
                                history += currentSyllable.copy(medial = medial)
                            }
                        } else if(currentSyllable.final.combinationPair != null) {
                            val pair = currentSyllable.final.combinationPair

                            history.removeAt(history.lastIndex)
                            val final = HangulJamo.Final(pair.first)
                            history += currentSyllable.copy(final = final)
                            composingWord.append(syllable?.string ?: "")
                            history.clear()
                            val initial = HangulJamo.Final(pair.second).toConsonant()?.toInitial()
                            val newSyllable = HangulSyllable(initial = initial)
                            history += newSyllable
                            history += newSyllable.copy(medial = medial)
                        } else {
                            history.removeAt(history.lastIndex)
                            composingWord.append(syllable?.string ?: "")
                            history.clear()
                            val initial = currentSyllable.final.toConsonant()?.toInitial()
                            val newSyllable = HangulSyllable(initial = initial)
                            history += newSyllable
                            history += newSyllable.copy(medial = medial)
                        }
                    }
                    is HangulJamo.Initial -> {
                        if(currentSyllable.initial != null) {
                            val combination = COMBINATION_TABLE_SEBEOLSIK[currentSyllable.initial.codePoint to jamo.codePoint]
                            if(combination != null && currentSyllable.medial == null && currentSyllable.final == null) {
                                history += currentSyllable.copy(initial = HangulJamo.Initial(combination))
                            } else {
                                composingWord.append(currentSyllable.string)
                                history.clear()
                                history += HangulSyllable(initial = jamo)
                            }
                        } else {
                            history += currentSyllable.copy(initial = jamo)
                        }
                    }
                    is HangulJamo.Medial -> {
                        if(currentSyllable.medial != null) {
                            val combination = COMBINATION_TABLE_SEBEOLSIK[currentSyllable.medial.codePoint to jamo.codePoint]
                            if(combination != null) {
                                history += currentSyllable.copy(medial = HangulJamo.Medial(combination))
                            } else {
                                composingWord.append(currentSyllable.string)
                                history.clear()
                                history += HangulSyllable(medial = jamo)
                            }
                        } else {
                            history += currentSyllable.copy(medial = jamo)
                        }
                    }
                    is HangulJamo.Final -> {
                        if(currentSyllable.final != null) {
                            val combination = COMBINATION_TABLE_SEBEOLSIK[currentSyllable.final.codePoint to jamo.codePoint]
                            if(combination != null) {
                                history += currentSyllable.copy(final = HangulJamo.Final(combination))
                            } else {
                                composingWord.append(currentSyllable.string)
                                history.clear()
                                history += HangulSyllable(final = jamo)
                            }
                        } else {
                            history += currentSyllable.copy(final = jamo)
                        }
                    }
                }
            }
            /* ▼ EventBus 한글 키 입력 이벤트 발생 -----------*/
            if (jamo is HangulJamo.Consonant || jamo is HangulJamo.Vowel ||
                jamo is HangulJamo.Initial || jamo is HangulJamo.Medial || jamo is HangulJamo.Final) {

                val text = combiningStateFeedback
                Log.d("KeywordSearch", "한글 자음/모음 입력됨(입력 반영 후): \"$text\"")
                EventBus.getDefault().post(HangulCommitEvent(HangulCommitEvent.TYPE_SYLLABLE, text.toString()))
            }
        }
        Log.d("HangulCombiner", "EXIT   processEvent: combiningStateFeedback=\"$combiningStateFeedback\"")
        return Event.createConsumedEvent(event)
    }

    override val combiningStateFeedback: CharSequence
        get() = syllable?.string ?: ""

    override fun reset() {
        history.clear()
    }

    sealed class HangulJamo {
        abstract val codePoint: Int
        abstract val modern: Boolean
        val string: String get() = codePoint.toChar().toString()
        data class NonHangul(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = false
        }
        data class Initial(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 0x1100 .. 0x1112
            val ordinal: Int get() = codePoint - 0x1100
            fun toConsonant(): Consonant? {
                val codePoint = COMPAT_CONSONANTS.getOrNull(CONVERT_INITIALS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.toInt() == 0) return null
                return Consonant(codePoint.toInt())
            }
        }
        data class Medial(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 1161 .. 0x1175
            val ordinal: Int get() = codePoint - 0x1161
            fun toVowel(): Vowel? {
                val codePoint = COMPAT_VOWELS.getOrNull(CONVERT_MEDIALS.indexOf(codePoint.toChar())) ?: return null
                return Vowel(codePoint.toInt())
            }
        }
        data class Final(override val codePoint: Int, val combinationPair: Pair<Int, Int>? = null) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 0x11a8 .. 0x11c2
            val ordinal: Int get() = codePoint - 0x11a7
            fun toConsonant(): Consonant? {
                val codePoint = COMPAT_CONSONANTS.getOrNull(CONVERT_FINALS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.toInt() == 0) return null
                return Consonant(codePoint.toInt())
            }
        }
        data class Consonant(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 0x3131 .. 0x314e
            val ordinal: Int get() = codePoint - 0x3131
            fun toInitial(): Initial? {
                val codePoint = CONVERT_INITIALS.getOrNull(COMPAT_CONSONANTS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.toInt() == 0) return null
                return Initial(codePoint.toInt())
            }
            fun toFinal(): Final? {
                val codePoint = CONVERT_FINALS.getOrNull(COMPAT_CONSONANTS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.toInt() == 0) return null
                return Final(codePoint.toInt())
            }
        }
        data class Vowel(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 0x314f .. 0x3163
            val ordinal: Int get() = codePoint - 0x314f1
            fun toMedial(): Medial? {
                val codePoint = CONVERT_MEDIALS.getOrNull(COMPAT_VOWELS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.toInt() == 0) return null
                return Medial(codePoint.toInt())
            }
        }
        companion object {
            const val COMPAT_CONSONANTS = "ㄱㄲㄳㄴㄵㄶㄷㄸㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅃㅄㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
            const val COMPAT_VOWELS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
            const val CONVERT_INITIALS = "ᄀᄁ\u0000ᄂ\u0000\u0000ᄃᄄᄅ\u0000\u0000\u0000\u0000\u0000\u0000\u0000ᄆᄇᄈ\u0000ᄉᄊᄋᄌᄍᄎᄏᄐᄑᄒ"
            const val CONVERT_MEDIALS = "ᅡᅢᅣᅤᅥᅦᅧᅨᅩᅪᅫᅬᅭᅮᅯᅰᅱᅲᅳᅴᅵ"
            const val CONVERT_FINALS = "ᆨᆩᆪᆫᆬᆭᆮ\u0000ᆯᆰᆱᆲᆳᆴᆵᆶᆷᆸ\u0000ᆹᆺᆻᆼᆽ\u0000ᆾᆿᇀᇁᇂ"
            fun of(codePoint: Int): HangulJamo {
                return when(codePoint) {
                    in 0x3131 .. 0x314e -> Consonant(codePoint)
                    in 0x314f .. 0x3163 -> Vowel(codePoint)
                    in 0x1100 .. 0x115f -> Initial(codePoint)
                    in 0x1160 .. 0x11a7 -> Medial(codePoint)
                    in 0x11a8 .. 0x11ff -> Final(codePoint)
                    else -> NonHangul(codePoint)
                }
            }
        }
    }

    data class HangulSyllable(
            val initial: HangulJamo.Initial? = null,
            val medial: HangulJamo.Medial? = null,
            val final: HangulJamo.Final? = null
    ) {
        val combinable: Boolean get() = (initial?.modern ?: false) && (medial?.modern ?: false) && (final?.modern ?: true)
        val combined: String get() = (0xac00 + (initial?.ordinal ?: 0) * 21 * 28
                + (medial?.ordinal ?: 0) * 28
                + (final?.ordinal ?: 0)).toChar().toString()
        val uncombined: String get() = (initial?.string ?: "") + (medial?.string ?: "") + (final?.string ?: "")
        val uncombinedCompat: String get() = (initial?.toConsonant()?.string ?: "") +
                (medial?.toVowel()?.string ?: "") + (final?.toConsonant()?.string ?: "")
        val string: String get() = if(this.combinable) this.combined else this.uncombinedCompat
    }

    companion object {
        val COMBINATION_TABLE_DUBEOLSIK = mapOf<Pair<Int, Int>, Int>(
                0x1169 to 0x1161 to 0x116a,
                0x1169 to 0x1162 to 0x116b,
                0x1169 to 0x1175 to 0x116c,
                0x116e to 0x1165 to 0x116f,
                0x116e to 0x1166 to 0x1170,
                0x116e to 0x1175 to 0x1171,
                0x1173 to 0x1175 to 0x1174,

                0x11a8 to 0x11ba to 0x11aa,
                0x11ab to 0x11bd to 0x11ac,
                0x11ab to 0x11c2 to 0x11ad,
                0x11af to 0x11a8 to 0x11b0,
                0x11af to 0x11b7 to 0x11b1,
                0x11af to 0x11b8 to 0x11b2,
                0x11af to 0x11ba to 0x11b3,
                0x11af to 0x11c0 to 0x11b4,
                0x11af to 0x11c1 to 0x11b5,
                0x11af to 0x11c2 to 0x11b6,
                0x11b8 to 0x11ba to 0x11b9
        )
        val COMBINATION_TABLE_SEBEOLSIK = mapOf<Pair<Int, Int>, Int>(
                0x1100 to 0x1100 to 0x1101,	// ㄲ
                0x1103 to 0x1103 to 0x1104,	// ㄸ
                0x1107 to 0x1107 to 0x1108,	// ㅃ
                0x1109 to 0x1109 to 0x110a,	// ㅆ
                0x110c to 0x110c to 0x110d,	// ㅉ

                0x1169 to 0x1161 to 0x116a,	// ㅘ
                0x1169 to 0x1162 to 0x116b,	// ㅙ
                0x1169 to 0x1175 to 0x116c,	// ㅚ
                0x116e to 0x1165 to 0x116f,	// ㅝ
                0x116e to 0x1166 to 0x1170,	// ㅞ
                0x116e to 0x1175 to 0x1171,	// ㅟ
                0x1173 to 0x1175 to 0x1174,	// ㅢ

                0x11a8 to 0x11a8 to 0x11a9,	// ㄲ
                0x11a8 to 0x11ba to 0x11aa,	// ㄳ
                0x11ab to 0x11bd to 0x11ac,	// ㄵ
                0x11ab to 0x11c2 to 0x11ad,	// ㄶ
                0x11af to 0x11a8 to 0x11b0,	// ㄺ
                0x11af to 0x11b7 to 0x11b1,	// ㄻ
                0x11af to 0x11b8 to 0x11b2,	// ㄼ
                0x11af to 0x11ba to 0x11b3,	// ㄽ
                0x11af to 0x11c0 to 0x11b4,	// ㄾ
                0x11af to 0x11c1 to 0x11b5,	// ㄿ
                0x11af to 0x11c2 to 0x11b6,	// ㅀ
                0x11b8 to 0x11ba to 0x11b9,	// ㅄ
                0x11ba to 0x11ba to 0x11bb	// ㅆ
        )
        private fun createEventChainFromSequence(text: CharSequence, originalEvent: Event): Event {
            return Event.createSoftwareTextEvent(text, Constants.CODE_OUTPUT_TEXT, originalEvent);
        }
    }

}
