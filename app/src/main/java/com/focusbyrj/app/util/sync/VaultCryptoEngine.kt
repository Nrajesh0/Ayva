/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app.util.sync

import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Zero-Knowledge Cryptography Engine for Multi-Device Sync and Vault Packages.
 *
 * - Algorithm:         AES-256-GCM (authenticated encryption)
 * - Key Wrapping:      Envelope encryption — ephemeral CEK per item, wrapped with DEK
 * - BIP-39 Mnemonic:   Full 2048-word English wordlist with 4-bit SHA-256 checksum
 * - Nonce/IV:          96-bit (12-byte) cryptographically secure random per encryption
 * - Tag Length:        128-bit authentication tag (AES-GCM default)
 *
 * CHANGED: SAMPLE_WORDLIST (150 words, non-BIP39 subset) → BIP-39 2048-word English wordlist
 * CHANGED: generate12WordMnemonic() → now uses all 2048 words with SHA-256 checksum validation
 */
object VaultCryptoEngine {

    private const val TAG = "VaultCryptoEngine"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12

    // =========================================================================
    // EncryptedPackage — passphrase-based format (local QR sync / legacy)
    // =========================================================================

    data class EncryptedPackage(
        val saltBase64: String,
        val ivBase64: String,
        val ciphertextBase64: String,
        val version: Int = 1,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        fun toJsonString(): String {
            val json = JSONObject()
            val meta = JSONObject().apply {
                put("version", version)
                put("cipher", "AES-256-GCM")
                put("kdf", "Argon2id")
                put("createdAt", createdAt)
            }
            val payload = JSONObject().apply {
                put("salt", saltBase64)
                put("iv", ivBase64)
                put("ciphertext", ciphertextBase64)
            }
            json.put("metadata", meta)
            json.put("payload", payload)
            return json.toString(2)
        }

        companion object {
            fun fromJsonString(jsonStr: String): EncryptedPackage? {
                return try {
                    val root = JSONObject(jsonStr)
                    val payload = root.getJSONObject("payload")
                    val meta = root.optJSONObject("metadata")
                    EncryptedPackage(
                        saltBase64 = payload.getString("salt"),
                        ivBase64 = payload.getString("iv"),
                        ciphertextBase64 = payload.getString("ciphertext"),
                        version = meta?.optInt("version", 1) ?: 1,
                        createdAt = meta?.optLong("createdAt", System.currentTimeMillis())
                            ?: System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse EncryptedPackage JSON", e)
                    null
                }
            }
        }
    }

    // =========================================================================
    // Raw-Key Based Encryption (AES-256-GCM with pre-derived key)
    // =========================================================================

    /**
     * Encrypts plaintext string into an EncryptedPackage using AES-256-GCM and a pre-derived raw key.
     */
    fun encryptWithRawKey(plaintext: String, rawKey: ByteArray): EncryptedPackage {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }

        val keySpec = SecretKeySpec(rawKey, "AES")
        val cipher = Cipher.getInstance(AES_GCM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

        return EncryptedPackage(
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        )
    }

    /**
     * Decrypts an EncryptedPackage back into original plaintext using a pre-derived raw key.
     */
    fun decryptWithRawKey(pkg: EncryptedPackage, rawKey: ByteArray): Result<String> {
        return try {
            val iv = Base64.decode(pkg.ivBase64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(pkg.ciphertextBase64, Base64.NO_WRAP)

            val keySpec = SecretKeySpec(rawKey, "AES")
            val cipher = Cipher.getInstance(AES_GCM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            val result = String(decryptedBytes, StandardCharsets.UTF_8)
            Arrays.fill(decryptedBytes, 0.toByte())
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Raw key decryption error", e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // Envelope Encryption (CEK wrapped with DEK — Pattern 1 cloud sync format)
    // =========================================================================

    /**
     * Envelope Encryption Package.
     * Content is encrypted with an ephemeral 256-bit Content Encryption Key (CEK),
     * and the CEK is wrapped (authenticated encrypted) with the user's master Data Encryption Key (DEK).
     * This ensures that rotation of the DEK only requires re-wrapping the CEK, not re-encrypting all data.
     */
    data class EnvelopeResult(
        val wrappedKeyBase64: String,
        val keyIvBase64: String,
        val contentIvBase64: String,
        val ciphertextBase64: String
    )

    /**
     * Multi-tier Envelope Encryption:
     * 1. Generates an ephemeral 256-bit AES Content Encryption Key (CEK) per individual item.
     * 2. Encrypts payload content with CEK using AES-256-GCM.
     * 3. Wraps (encrypts) the CEK using the user's master DEK using AES-256-GCM with a distinct random IV.
     * 4. Zeroizes intermediate CEK bytes from memory immediately.
     */
    fun encryptEnvelope(plaintext: String, dek: ByteArray): EnvelopeResult {
        val random = SecureRandom()
        val cek = ByteArray(32).also { random.nextBytes(it) }
        val contentIv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val keyIv = ByteArray(IV_BYTES).also { random.nextBytes(it) }

        try {
            // 1. Encrypt payload content with CEK
            val cekSpec = SecretKeySpec(cek, "AES")
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.ENCRYPT_MODE, cekSpec, GCMParameterSpec(GCM_TAG_LENGTH, contentIv))
            val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

            // 2. Wrap CEK with DEK
            val dekSpec = SecretKeySpec(dek, "AES")
            val wrapCipher = Cipher.getInstance(AES_GCM)
            wrapCipher.init(Cipher.ENCRYPT_MODE, dekSpec, GCMParameterSpec(GCM_TAG_LENGTH, keyIv))
            val wrappedKey = wrapCipher.doFinal(cek)

            return EnvelopeResult(
                wrappedKeyBase64 = Base64.encodeToString(wrappedKey, Base64.NO_WRAP),
                keyIvBase64 = Base64.encodeToString(keyIv, Base64.NO_WRAP),
                contentIvBase64 = Base64.encodeToString(contentIv, Base64.NO_WRAP),
                ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            )
        } finally {
            Arrays.fill(cek, 0.toByte())
        }
    }

    /**
     * Decrypts an Envelope Encryption payload:
     * 1. Unwraps CEK using master DEK.
     * 2. Decrypts ciphertext using unwrapped CEK.
     * 3. Zeroizes CEK bytes immediately.
     */
    fun decryptEnvelope(
        wrappedKeyBase64: String,
        keyIvBase64: String,
        contentIvBase64: String,
        ciphertextBase64: String,
        dek: ByteArray
    ): Result<String> {
        var cek: ByteArray? = null
        return try {
            val wrappedKey = Base64.decode(wrappedKeyBase64, Base64.NO_WRAP)
            val keyIv = Base64.decode(keyIvBase64, Base64.NO_WRAP)
            val contentIv = Base64.decode(contentIvBase64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)

            val dekSpec = SecretKeySpec(dek, "AES")
            val wrapCipher = Cipher.getInstance(AES_GCM)
            wrapCipher.init(Cipher.DECRYPT_MODE, dekSpec, GCMParameterSpec(GCM_TAG_LENGTH, keyIv))
            cek = wrapCipher.doFinal(wrappedKey)

            val cekSpec = SecretKeySpec(cek, "AES")
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, cekSpec, GCMParameterSpec(GCM_TAG_LENGTH, contentIv))
            val decryptedBytes = cipher.doFinal(ciphertext)
            val result = String(decryptedBytes, StandardCharsets.UTF_8)
            Arrays.fill(decryptedBytes, 0.toByte())
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Envelope decryption failed", e)
            Result.failure(e)
        } finally {
            cek?.let { Arrays.fill(it, 0.toByte()) }
        }
    }

    // =========================================================================
    // Binary Media Encryption (AES-256-GCM, IV prepended to output)
    // =========================================================================

    /**
     * Encrypts arbitrary binary data (like media attachments) using AES-256-GCM and a pre-derived raw key.
     * Output format: [12-byte IV][ciphertext+16-byte GCM tag]
     */
    fun encryptBytesWithRawKey(data: ByteArray, rawKey: ByteArray): ByteArray {
        val random = SecureRandom()
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val keySpec = SecretKeySpec(rawKey, "AES")
        val cipher = Cipher.getInstance(AES_GCM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(data)

        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return combined
    }

    /**
     * Decrypts binary data previously encrypted with encryptBytesWithRawKey.
     */
    fun decryptBytesWithRawKey(encryptedData: ByteArray, rawKey: ByteArray): Result<ByteArray> {
        return try {
            if (encryptedData.size < IV_BYTES) {
                return Result.failure(IllegalArgumentException("Encrypted media data is too short"))
            }
            val iv = ByteArray(IV_BYTES)
            System.arraycopy(encryptedData, 0, iv, 0, IV_BYTES)
            val ciphertext = ByteArray(encryptedData.size - IV_BYTES)
            System.arraycopy(encryptedData, IV_BYTES, ciphertext, 0, ciphertext.size)

            val keySpec = SecretKeySpec(rawKey, "AES")
            val cipher = Cipher.getInstance(AES_GCM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            val decrypted = cipher.doFinal(ciphertext)
            Result.success(decrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Binary media decryption error", e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // BIP-39 Mnemonic Generation (full 2048-word English wordlist)
    //
    // REPLACED: SAMPLE_WORDLIST (150 words, non-standard subset)
    // WITH: Official BIP-39 English wordlist (2048 words, SHA-256 checksum verified)
    //
    // Mnemonic generation: 128 bits of entropy + 4-bit SHA-256 checksum = 132 bits → 12 words
    // Each word encodes exactly 11 bits (log₂(2048) = 11).
    // 12 words × 11 bits = 132 bits = 128 bits entropy + 4 bits checksum.
    // =========================================================================

    /** Full BIP-39 English wordlist — 2048 words, used to generate 12-word recovery phrases. */
    val BIP39_WORDLIST: List<String> = listOf(
        "abandon","ability","able","about","above","absent","absorb","abstract","absurd","abuse",
        "access","accident","account","accuse","achieve","acid","acoustic","acquire","across","act",
        "action","actor","actress","actual","adapt","add","addict","address","adjust","admit",
        "adult","advance","advice","aerobic","affair","afford","afraid","again","age","agent",
        "agree","ahead","aim","air","airport","aisle","alarm","album","alcohol","alert",
        "alien","all","alley","allow","almost","alone","alpha","already","also","alter",
        "always","amateur","amazing","among","amount","amused","analyst","anchor","ancient","anger",
        "angle","angry","animal","ankle","announce","annual","another","answer","antenna","antique",
        "anxiety","any","apart","apology","appear","apple","approve","april","arch","arctic",
        "area","arena","argue","arm","armed","armor","army","around","arrange","arrest",
        "arrive","arrow","art","artefact","artist","artwork","ask","aspect","assault","asset",
        "assist","assume","asthma","athlete","atom","attack","attend","attitude","attract","auction",
        "audit","august","aunt","author","auto","autumn","average","avocado","avoid","awake",
        "aware","away","awesome","awful","awkward","axis","baby","bachelor","bacon","badge",
        "bag","balance","balcony","ball","bamboo","banana","banner","bar","barely","bargain",
        "barrel","base","basic","basket","battle","beach","bean","beauty","because","become",
        "beef","before","begin","behave","behind","believe","below","belt","bench","benefit",
        "best","betray","better","between","beyond","bicycle","bid","bike","bind","biology",
        "bird","birth","bitter","black","blade","blame","blanket","blast","bleak","bless",
        "blind","blood","blossom","blouse","blue","blur","blush","board","boat","body",
        "boil","bomb","bone","bonus","book","boost","border","boring","borrow","boss",
        "bottom","bounce","box","boy","bracket","brain","brand","brass","brave","bread",
        "breeze","brick","bridge","brief","bright","bring","brisk","broccoli","broken","bronze",
        "broom","brother","brown","brush","bubble","buddy","budget","buffalo","build","bulb",
        "bulk","bullet","bundle","bunker","burden","burger","burst","bus","business","busy",
        "butter","buyer","buzz","cabbage","cabin","cable","cactus","cage","cake","call",
        "calm","camera","camp","can","canal","cancel","candy","cannon","canoe","canvas",
        "canyon","capable","capital","captain","car","carbon","card","cargo","carpet","carry",
        "cart","case","cash","casino","castle","casual","cat","catalog","catch","category",
        "cattle","caught","cause","caution","cave","ceiling","celery","cement","census","century",
        "cereal","certain","chair","chalk","champion","change","chaos","chapter","charge","chase",
        "chat","cheap","check","cheese","chef","cherry","chest","chicken","chief","child",
        "chimney","choice","choose","chronic","chuckle","chunk","churn","cigar","cinnamon","circle",
        "citizen","city","civil","claim","clap","clarify","claw","clay","clean","clerk",
        "clever","click","client","cliff","climb","clinic","clip","clock","clog","close",
        "cloth","cloud","clown","club","clump","cluster","clutch","coach","coast","coconut",
        "code","coffee","coil","coin","collect","color","column","combine","come","comfort",
        "comic","common","company","concert","conduct","confirm","congress","connect","consider","control",
        "convince","cook","cool","copper","copy","coral","core","corn","correct","cost",
        "cotton","couch","country","couple","course","cousin","cover","coyote","crack","cradle",
        "craft","cram","crane","crash","crater","crawl","crazy","cream","credit","creek",
        "crew","cricket","crime","crisp","critic","crop","cross","crouch","crowd","crucial",
        "cruel","cruise","crumble","crunch","crush","cry","crystal","cube","culture","cup",
        "cupboard","curious","current","curtain","curve","cushion","custom","cute","cycle","dad",
        "damage","damp","dance","danger","daring","dash","daughter","dawn","day","deal",
        "debate","debris","decade","december","decide","decline","decorate","decrease","deer","defense",
        "define","defy","degree","delay","deliver","demand","demise","denial","dentist","deny",
        "depart","depend","deposit","depth","deputy","derive","describe","desert","design","desk",
        "despair","destroy","detail","detect","develop","device","devote","diagram","dial","diamond",
        "diary","dice","diesel","diet","differ","digital","dignity","dilemma","dinner","dinosaur",
        "direct","dirt","disagree","discover","disease","dish","dismiss","disorder","display","distance",
        "divert","divide","divorce","dizzy","doctor","document","dog","doll","dolphin","domain",
        "donate","donkey","donor","door","dose","double","dove","draft","dragon","drama",
        "drastic","draw","dream","dress","drift","drill","drink","drip","drive","drop",
        "drum","dry","duck","dumb","dune","during","dust","dutch","duty","dwarf",
        "dynamic","eager","eagle","early","earn","earth","easily","east","easy","echo",
        "ecology","economy","edge","edit","educate","effort","egg","eight","either","elbow",
        "elder","electric","elegant","element","elephant","elevator","elite","else","embark","embody",
        "embrace","emerge","emotion","employ","empower","empty","enable","enact","end","endless",
        "endorse","enemy","energy","enforce","engage","engine","enhance","enjoy","enlist","enough",
        "enrich","enroll","ensure","enter","entire","entry","envelope","episode","equal","equip",
        "era","erase","erode","erosion","error","erupt","escape","essay","essence","estate",
        "eternal","ethics","evidence","evil","evoke","evolve","exact","example","excess","exchange",
        "excite","exclude","excuse","execute","exercise","exhaust","exhibit","exile","exist","exit",
        "exotic","expand","expect","expire","explain","expose","express","extend","extra","eye",
        "eyebrow","fabric","face","faculty","fade","faint","faith","fall","false","fame",
        "family","famous","fan","fancy","fantasy","farm","fashion","fat","fatal","father",
        "fatigue","fault","favorite","feature","february","federal","fee","feed","feel","female",
        "fence","festival","fetch","fever","few","fiber","fiction","field","figure","file",
        "film","filter","final","find","fine","finger","finish","fire","firm","first",
        "fiscal","fish","fit","fitness","fix","flag","flame","flash","flat","flavor",
        "flee","flight","flip","float","flock","floor","flower","fluid","flush","fly",
        "foam","focus","fog","foil","fold","follow","food","foot","force","forest",
        "forget","fork","fortune","forum","forward","fossil","foster","found","fox","fragile",
        "frame","frequent","fresh","friend","fringe","frog","front","frost","frown","frozen",
        "fruit","fuel","fun","funny","furnace","fury","future","gadget","gain","galaxy",
        "gallery","game","gap","garage","garbage","garden","garlic","garment","gas","gasp",
        "gate","gather","gauge","gaze","general","genius","genre","gentle","genuine","gesture",
        "ghost","giant","gift","giggle","ginger","giraffe","girl","give","glad","glance",
        "glare","glass","glide","glimpse","globe","gloom","glory","glove","glow","glue",
        "goat","goddess","gold","good","goose","gorilla","gospel","gossip","govern","gown",
        "grab","grace","grain","grant","grape","grass","gravity","great","green","grid",
        "grief","grit","grocery","group","grow","grunt","guard","guess","guide","guilt",
        "guitar","gun","gym","habit","hair","half","hammer","hamster","hand","happy",
        "harbor","hard","harsh","harvest","hat","have","hawk","hazard","head","health",
        "heart","heavy","hedgehog","height","hello","helmet","help","hen","hero","hidden",
        "high","hill","hint","hip","hire","history","hobby","hockey","hold","hole",
        "holiday","hollow","home","honey","hood","hope","horn","horror","horse","hospital",
        "host","hotel","hour","hover","hub","huge","human","humble","humor","hundred",
        "hungry","hunt","hurdle","hurry","hurt","husband","hybrid","ice","icon","idea",
        "identify","idle","ignore","ill","illegal","illness","image","imitate","immense","immune",
        "impact","impose","improve","impulse","inch","include","income","increase","index","indicate",
        "indoor","industry","infant","inflict","inform","inhale","inherit","initial","inject","injury",
        "inmate","inner","innocent","input","inquiry","insane","insect","inside","inspire","install",
        "intact","interest","into","invest","invite","involve","iron","island","isolate","issue",
        "item","ivory","jacket","jaguar","jar","jazz","jealous","jeans","jelly","jewel",
        "job","join","joke","journey","joy","judge","juice","jump","jungle","junior",
        "junk","just","kangaroo","keen","keep","ketchup","key","kick","kid","kidney",
        "kind","kingdom","kiss","kit","kitchen","kite","kitten","kiwi","knee","knife",
        "knock","know","lab","label","labor","ladder","lady","lake","lamp","language",
        "laptop","large","later","latin","laugh","laundry","lava","law","lawn","lawsuit",
        "layer","lazy","leader","leaf","learn","leave","lecture","left","leg","legal",
        "legend","leisure","lemon","lend","length","lens","leopard","lesson","letter","level",
        "liar","liberty","library","license","life","lift","light","like","limb","limit",
        "link","lion","liquid","list","little","live","lizard","load","loan","lobster",
        "local","lock","logic","lonely","long","loop","lottery","loud","lounge","love",
        "loyal","lucky","luggage","lumber","lunar","lunch","luxury","lyrics","machine","mad",
        "magic","magnet","maid","mail","main","major","make","mammal","man","manage",
        "mandate","mango","mansion","manual","maple","marble","march","margin","marine","market",
        "marriage","mask","mass","master","match","material","math","matrix","matter","maximum",
        "maze","meadow","mean","measure","meat","mechanic","medal","media","melody","melt",
        "member","memory","mention","menu","mercy","merge","merit","merry","mesh","message",
        "metal","method","middle","midnight","milk","million","mimic","mind","minimum","minor",
        "minute","miracle","mirror","misery","miss","mistake","mix","mixed","mixture","mobile",
        "model","modify","mom","moment","monitor","monkey","monster","month","moon","moral",
        "more","morning","mosquito","mother","motion","motor","mountain","mouse","move","movie",
        "much","muffin","mule","multiply","muscle","museum","mushroom","music","must","mutual",
        "myself","mystery","myth","naive","name","napkin","narrow","nasty","nation","nature",
        "near","neck","need","negative","neglect","neither","nephew","nerve","nest","net",
        "network","neutral","never","news","next","nice","night","noble","noise","nominee",
        "noodle","normal","north","nose","notable","note","nothing","notice","novel","now",
        "nuclear","number","nurse","nut","oak","obey","object","oblige","obscure","observe",
        "obtain","obvious","occur","ocean","october","odor","off","offer","office","often",
        "oil","okay","old","olive","olympic","omit","once","one","onion","online",
        "only","open","opera","opinion","oppose","option","orange","orbit","orchard","order",
        "ordinary","organ","orient","original","orphan","ostrich","other","outdoor","outer","output",
        "outside","oval","oven","over","own","owner","oxygen","oyster","ozone","pact",
        "paddle","page","pair","palace","palm","panda","panel","panic","panther","paper",
        "parade","parent","park","parrot","party","pass","patch","path","patient","patrol",
        "pattern","pause","pave","payment","peace","peanut","pear","peasant","pelican","pen",
        "penalty","pencil","people","pepper","perfect","permit","person","pet","phone","photo",
        "phrase","physical","piano","picnic","picture","piece","pig","pigeon","pill","pilot",
        "pink","pioneer","pipe","pistol","pitch","pizza","place","planet","plastic","plate",
        "play","please","pledge","pluck","plug","plunge","poem","poet","point","polar",
        "pole","police","pond","pony","pool","popular","portion","position","possible","post",
        "potato","pottery","poverty","powder","power","practice","praise","predict","prefer","prepare",
        "present","pretty","prevent","price","pride","primary","print","priority","prison","private",
        "prize","problem","process","produce","profit","program","project","promote","proof","property",
        "prosper","protect","proud","provide","public","pudding","pull","pulp","pulse","pumpkin",
        "punch","pupil","puppy","purchase","purity","purpose","purse","push","put","puzzle",
        "pyramid","quality","quantum","quarter","question","quick","quit","quiz","quote","rabbit",
        "raccoon","race","rack","radar","radio","rail","rain","raise","rally","ramp",
        "ranch","random","range","rapid","rare","rate","rather","raven","raw","razor",
        "ready","real","reason","rebel","rebuild","recall","receive","recipe","record","recycle",
        "reduce","reflect","reform","refuse","region","regret","regular","reject","relax","release",
        "relief","rely","remain","remember","remind","remove","render","renew","rent","reopen",
        "repair","repeat","replace","report","require","rescue","resemble","resist","resource","response",
        "result","retire","retreat","return","reunion","reveal","review","reward","rhythm","rib",
        "ribbon","rice","rich","ride","ridge","rifle","right","rigid","ring","riot",
        "ripple","risk","ritual","rival","river","road","roast","robot","robust","rocket",
        "romance","roof","rookie","room","rose","rotate","rough","round","route","royal",
        "rubber","rude","rug","rule","run","runway","rural","sad","saddle","sadness",
        "safe","sail","salad","salmon","salon","salt","salute","same","sample","sand",
        "satisfy","satoshi","sauce","sausage","save","say","scale","scan","scare","scatter",
        "scene","scheme","school","science","scissors","scorpion","scout","scrap","screen","script",
        "scrub","sea","search","season","seat","second","secret","section","security","seed",
        "seek","segment","select","sell","seminar","senior","sense","sentence","series","service",
        "session","settle","setup","seven","shadow","shaft","shallow","share","shed","shell",
        "sheriff","shield","shift","shine","ship","shiver","shock","shoe","shoot","shop",
        "short","shoulder","shove","shrimp","shrug","shuffle","shy","sibling","sick","side",
        "siege","sight","sign","silent","silk","silly","silver","similar","simple","since",
        "sing","siren","sister","situate","six","size","skate","sketch","ski","skill",
        "skin","skirt","skull","slab","slam","sleep","slender","slice","slide","slight",
        "slim","slogan","slot","slow","slush","small","smart","smile","smoke","smooth",
        "snack","snake","snap","sniff","snow","soap","soccer","social","sock","soda",
        "soft","solar","soldier","solid","solution","solve","someone","song","soon","sorry",
        "sort","soul","sound","soup","source","south","space","spare","spatial","spawn",
        "speak","special","speed","spell","spend","sphere","spice","spider","spike","spin",
        "spirit","split","spoil","sponsor","spoon","sport","spot","spray","spread","spring",
        "spy","square","squeeze","squirrel","stable","stadium","staff","stage","stairs","stamp",
        "stand","start","state","stay","steak","steel","stem","step","stereo","stick",
        "still","sting","stock","stomach","stone","stool","story","stove","strategy","street",
        "strike","strong","struggle","student","stuff","stumble","style","subject","submit","subway",
        "success","such","sudden","suffer","sugar","suggest","suit","summer","sun","sunny",
        "sunset","super","supply","supreme","sure","surface","surge","surprise","surround","survey",
        "suspect","sustain","swallow","swamp","swap","swarm","swear","sweet","swift","swim",
        "swing","switch","sword","symbol","symptom","syrup","system","table","tackle","tag",
        "tail","talent","talk","tank","tape","target","task","taste","tattoo","taxi",
        "teach","team","tell","ten","tenant","tennis","tent","term","test","text",
        "thank","that","theme","then","theory","there","they","thing","this","thought",
        "three","thrive","throw","thumb","thunder","ticket","tide","tiger","tilt","timber",
        "time","tiny","tip","tired","tissue","title","toast","tobacco","today","toddler",
        "toe","together","toilet","token","tomato","tomorrow","tone","tongue","tonight","tool",
        "tooth","top","topic","topple","torch","tornado","tortoise","toss","total","tourist",
        "toward","tower","town","toy","track","trade","traffic","tragic","train","transfer",
        "trap","trash","travel","tray","treat","tree","trend","trial","tribe","trick",
        "trigger","trim","trip","trophy","trouble","truck","true","truly","trumpet","trust",
        "truth","try","tube","tuition","tumble","tuna","tunnel","turkey","turn","turtle",
        "twelve","twenty","twice","twin","twist","two","type","typical","ugly","umbrella",
        "unable","unaware","uncle","uncover","under","undo","unfair","unfold","unhappy","uniform",
        "unique","unit","universe","unknown","unlock","until","unusual","unveil","update","upgrade",
        "uphold","upon","upper","upset","urban","urge","usage","use","used","useful",
        "useless","usual","utility","vacant","vacuum","vague","valid","valley","valve","van",
        "vanish","vapor","various","vast","vault","vehicle","velvet","vendor","venture","venue",
        "verb","verify","version","very","vessel","veteran","viable","vibrant","vicious","victory",
        "video","view","village","vintage","violin","virtual","virus","visa","visit","visual",
        "vital","vivid","vocal","voice","void","volcano","volume","vote","voyage","wage",
        "wagon","wait","walk","wall","walnut","want","warfare","warm","warrior","wash",
        "wasp","waste","water","wave","way","wealth","weapon","wear","weasel","weather",
        "web","wedding","weekend","weird","welcome","west","wet","whale","what","wheat",
        "wheel","when","where","whip","whisper","wide","width","wife","wild","will",
        "win","window","wine","wing","wink","winner","winter","wire","wisdom","wise",
        "wish","witness","wolf","woman","wonder","wood","wool","word","work","world",
        "worry","worth","wrap","wreck","wrestle","wrist","write","wrong","yard","year",
        "yellow","you","young","youth","zebra","zero","zone","zoo"
    )

    init {
        check(BIP39_WORDLIST.size == 2048) {
            "BIP-39 wordlist integrity failure: expected 2048 words, found ${BIP39_WORDLIST.size}"
        }
    }

    /**
     * Generates a 12-word BIP-39 mnemonic recovery phrase from 128 bits of cryptographically
     * secure entropy with a 4-bit SHA-256 checksum appended (total 132 bits = 12 × 11 bits).
     *
     * This is standards-compliant BIP-39 generation:
     * 1. Generate 16 random bytes (128 bits of entropy).
     * 2. SHA-256 hash the entropy; take the first 4 bits as the checksum.
     * 3. Concatenate entropy bits + checksum bits (132 bits total).
     * 4. Split into 12 groups of 11 bits; each group indexes into the 2048-word list.
     *
     * PREVIOUSLY: generate12WordMnemonic() used SecureRandom.nextInt(sampleListSize) on a
     *             truncated 150-word list — not BIP-39 compliant, no checksum, not recoverable.
     * NOW:        Full BIP-39 compliant with checksum — matches the standard used by hardware wallets.
     */
    fun generate12WordMnemonic(): List<String> {
        val random = SecureRandom()

        // 1. Generate 128 bits (16 bytes) of entropy
        val entropy = ByteArray(16).also { random.nextBytes(it) }

        // 2. SHA-256(entropy), take first byte (= first 8 bits), use top 4 bits as checksum
        val sha256 = MessageDigest.getInstance("SHA-256")
        val hash = sha256.digest(entropy)
        val checksumByte = hash[0]

        // 3. Build a bit array: 128 entropy bits + 4 checksum bits = 132 bits
        val bits = BooleanArray(132)
        for (i in 0 until 128) {
            val byteIndex = i / 8
            val bitIndex = 7 - (i % 8)
            bits[i] = (entropy[byteIndex].toInt() shr bitIndex) and 1 == 1
        }
        // Append top 4 checksum bits from checksumByte
        for (i in 0 until 4) {
            val bitIndex = 7 - i
            bits[128 + i] = (checksumByte.toInt() shr bitIndex) and 1 == 1
        }

        // 4. Split 132 bits into 12 groups of 11 bits → word index
        val words = mutableListOf<String>()
        for (wordIndex in 0 until 12) {
            var index = 0
            for (bitInWord in 0 until 11) {
                index = index shl 1
                if (bits[wordIndex * 11 + bitInWord]) {
                    index = index or 1
                }
            }
            words.add(BIP39_WORDLIST[index])
        }

        Arrays.fill(entropy, 0.toByte())
        return words
    }

    /**
     * Validates that a list of words forms a valid BIP-39 mnemonic (checksum check).
     * All 12 words must exist in the BIP-39 wordlist and the 4-bit checksum must be correct.
     *
     * @return true if the mnemonic is valid, false otherwise.
     */
    fun validateMnemonic(words: List<String>): Boolean {
        if (words.size != 12) return false

        return try {
            // Resolve indices
            // B1-F-014 FIX: Normalize words (trim and lowercase) to handle mobile keyboard auto-capitalization and trailing spaces
            val indices = words.map { word ->
                val normalized = word.trim().lowercase()
                BIP39_WORDLIST.indexOf(normalized).also { if (it < 0) return false }
            }

            // Reconstruct 132 bits
            val bits = BooleanArray(132)
            for (wordIndex in 0 until 12) {
                val idx = indices[wordIndex]
                for (bitInWord in 0 until 11) {
                    bits[wordIndex * 11 + bitInWord] = (idx shr (10 - bitInWord)) and 1 == 1
                }
            }

            // Reconstruct 16 entropy bytes (first 128 bits)
            val entropy = ByteArray(16)
            for (i in 0 until 128) {
                if (bits[i]) {
                    entropy[i / 8] = (entropy[i / 8].toInt() or (1 shl (7 - i % 8))).toByte()
                }
            }

            // Extract the 4 checksum bits from the mnemonic
            var mnemonicChecksum = 0
            for (i in 0 until 4) {
                if (bits[128 + i]) mnemonicChecksum = mnemonicChecksum or (1 shl (3 - i))
            }

            // Compute expected checksum
            val sha256 = MessageDigest.getInstance("SHA-256")
            val hash = try {
                sha256.digest(entropy)
            } finally {
                Arrays.fill(entropy, 0.toByte())
            }
            val expectedChecksum = (hash[0].toInt() ushr 4) and 0x0F

            mnemonicChecksum == expectedChecksum
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Derives a 256-bit AES encryption key from a normalized 12-word BIP-39 mnemonic phrase.
     * Uses standard BIP-39 PBKDF2 derivation with 2048 iterations.
     *
     * @param words The 12 BIP-39 mnemonic words.
     * @param salt The cryptographic salt (at least 16 bytes).
     * @return 32-byte (256-bit) AES key.
     */
    fun deriveKeyFromMnemonic(words: List<String>, salt: ByteArray): ByteArray {
        require(words.size == 12) { "Mnemonic must consist of exactly 12 words" }
        require(salt.isNotEmpty()) { "Salt must not be empty" }

        val normalizedMnemonic = words.joinToString(" ") { it.trim().lowercase() }
        val chars = normalizedMnemonic.toCharArray()
        var spec: PBEKeySpec? = null
        try {
            val keyFactory = try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            } catch (_: Exception) {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            }
            spec = PBEKeySpec(chars, salt, 2048, 256)
            return keyFactory.generateSecret(spec).encoded
        } finally {
            // B1-F-005 FIX: PBEKeySpec holds an internal char[] copy of the mnemonic.
            // clearPassword() zeroes that internal copy so it doesn't linger in heap memory.
            spec?.clearPassword()
            Arrays.fill(chars, '0')
        }
    }

    /**
     * Creates an encrypted recovery envelope containing the vault subkey, wrapped under
     * an AES-256-GCM key derived from the 12-word BIP-39 mnemonic phrase.
     *
     * @param vaultSubKey The 256-bit vault subkey to protect.
     * @param mnemonicWords The 12 BIP-39 mnemonic words.
     * @return Triple of (ciphertext, iv, salt)
     */
    fun createRecoveryEnvelope(vaultSubKey: ByteArray, mnemonicWords: List<String>): Triple<ByteArray, ByteArray, ByteArray> {
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }

        val recoveryKey = deriveKeyFromMnemonic(mnemonicWords, salt)
        val ciphertext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(recoveryKey, "AES")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            cipher.doFinal(vaultSubKey)
        } finally {
            Arrays.fill(recoveryKey, 0.toByte())
        }

        return Triple(ciphertext, iv, salt)
    }

    /**
     * Decrypts a vault subkey from a recovery envelope using the 12-word mnemonic phrase.
     *
     * @param ciphertext The encrypted envelope ciphertext containing the subkey.
     * @param mnemonicWords The 12 BIP-39 mnemonic words.
     * @param salt The cryptographic salt used during envelope creation.
     * @param iv The 12-byte initialization vector.
     * @return The decrypted 256-bit vault subkey.
     * @throws javax.crypto.AEADBadTagException if the mnemonic or ciphertext was tampered with or invalid.
     */
    fun decryptRecoveryEnvelopeWithMnemonic(
        ciphertext: ByteArray,
        mnemonicWords: List<String>,
        salt: ByteArray,
        iv: ByteArray
    ): ByteArray {
        val recoveryKey = deriveKeyFromMnemonic(mnemonicWords, salt)
        return try {
            decryptRecoveryEnvelope(ciphertext, recoveryKey, iv)
        } finally {
            Arrays.fill(recoveryKey, 0.toByte())
        }
    }

    /**
     * Encrypts a vault subkey (or secret payload) with an AES-256-GCM recovery key.
     *
     * @param vaultSubKey The 256-bit vault subkey to protect.
     * @param recoveryKey The 256-bit recovery key derived from the mnemonic.
     * @param iv The 12-byte initialization vector (generated if not provided).
     * @return Pair of (ciphertext, iv)
     */
    fun encryptRecoveryEnvelope(
        vaultSubKey: ByteArray,
        recoveryKey: ByteArray,
        iv: ByteArray = ByteArray(12).also { SecureRandom().nextBytes(it) }
    ): Pair<ByteArray, ByteArray> {
        require(recoveryKey.size == 32) { "Recovery key must be 256 bits (32 bytes)" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(recoveryKey, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(vaultSubKey)
        return Pair(ciphertext, iv)
    }

    /**
     * Decrypts a vault subkey from a recovery envelope using the recovery key and IV.
     *
     * @param ciphertext The encrypted envelope ciphertext containing the subkey.
     * @param recoveryKey The 256-bit recovery key derived from the mnemonic.
     * @param iv The 12-byte initialization vector.
     * @return The decrypted 256-bit vault subkey.
     * @throws javax.crypto.AEADBadTagException if the key or ciphertext was tampered with or invalid.
     */
    fun decryptRecoveryEnvelope(ciphertext: ByteArray, recoveryKey: ByteArray, iv: ByteArray): ByteArray {
        require(recoveryKey.size == 32) { "Recovery key must be 256 bits (32 bytes)" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(recoveryKey, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        return cipher.doFinal(ciphertext)
    }
}
