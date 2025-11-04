package com.prelina.findmynet.icloud3.auth

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.experimental.xor

/**
 * SRP (Secure Remote Password) Protocol Implementation
 * 完整移植自 pyicloud_srp.py (v1.0.22)
 * 
 * SRP-6a 协议实现
 * 
 * 符号说明:
 * N    A large safe prime (N = 2q+1, where q is prime) - 大安全素数
 *      All arithmetic is done modulo N. - 所有算术运算都是模N
 * g    A generator modulo N - 模N的生成元
 * k    Multiplier parameter (k = H(N, g) in SRP-6a, k = 3 for legacy SRP-6) - 乘数参数
 * s    User's salt - 用户盐值
 * I    Username - 用户名
 * p    Cleartext Password - 明文密码
 * H()  One-way hash function - 单向哈希函数
 * ^    (Modular) Exponentiation - (模)指数运算
 * u    Random scrambling parameter - 随机混淆参数
 * a,b  Secret ephemeral values - 秘密临时值
 * A,B  Public ephemeral values - 公开临时值
 * x    Private key (derived from p and s) - 私钥(由p和s派生)
 * v    Password verifier - 密码验证器
 */

/**
 * 哈希算法类型
 */
enum class SRPHashAlgorithm(val algorithmName: String) {
    SHA1("SHA-1"),
    SHA224("SHA-224"),
    SHA256("SHA-256"),
    SHA384("SHA-384"),
    SHA512("SHA-512")
}

/**
 * N和g常量类型
 */
enum class SRPNGType {
    NG_1024,  // 1024位
    NG_2048,  // 2048位 (默认)
    NG_4096,  // 4096位
    NG_8192,  // 8192位
    NG_CUSTOM // 自定义
}

/**
 * SRP协议配置
 */
data class SRPConfig(
    val rfc5054Compat: Boolean = false,
    val noUsernameInX: Boolean = false
)

/**
 * SRP协议工具类
 */
object SRPProtocol {
    
    private val config = SRPConfig()
    
    /**
     * N和g常量 - RFC 5054标准值
     */
    private val ngConstants = mapOf(
        // 1024-bit
        SRPNGType.NG_1024 to Pair(
            "EEAF0AB9ADB38DD69C33F80AFA8FC5E86072618775FF3C0B9EA2314C9C256576D674DF7496" +
            "EA81D3383B4813D692C6E0E0D5D8E250B98BE48E495C1D6089DAD15DC7D7B46154D6B6CE8E" +
            "F4AD69B15D4982559B297BCF1885C529F566660E57EC68EDBC3C05726CC02FD4CBF4976EAA" +
            "9AFD5138FE8376435B9FC61D2FC0EB06E3",
            "2"
        ),
        // 2048-bit (默认)
        SRPNGType.NG_2048 to Pair(
            "AC6BDB41324A9A9BF166DE5E1389582FAF72B6651987EE07FC3192943DB56050A37329CBB4" +
            "A099ED8193E0757767A13DD52312AB4B03310DCD7F48A9DA04FD50E8083969EDB767B0CF60" +
            "95179A163AB3661A05FBD5FAAAE82918A9962F0B93B855F97993EC975EEAA80D740ADBF4FF" +
            "747359D041D5C33EA71D281E446B14773BCA97B43A23FB801676BD207A436C6481F1D2B907" +
            "8717461A5B9D32E688F87748544523B524B0D57D5EA77A2775D2ECFA032CFBDBF52FB37861" +
            "60279004E57AE6AF874E7303CE53299CCC041C7BC308D82A5698F3A8D0C38271AE35F8E9DB" +
            "FBB694B5C803D89F7AE435DE236D525F54759B65E372FCD68EF20FA7111F9E4AFF73",
            "2"
        ),
        // 4096-bit
        SRPNGType.NG_4096 to Pair(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E08" +
            "8A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B" +
            "302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9" +
            "A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE6" +
            "49286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8" +
            "FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
            "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3BE39E772C" +
            "180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF695581718" +
            "3995497CEA956AE515D2261898FA051015728E5A8AAAC42DAD33170D" +
            "04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7D" +
            "B3970F85A6E1E4C7ABF5AE8CDB0933D71E8C94E04A25619DCEE3D226" +
            "1AD2EE6BF12FFA06D98A0864D87602733EC86A64521F2B18177B200C" +
            "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB3143DB5BFC" +
            "E0FD108E4B82D120A92108011A723C12A787E6D788719A10BDBA5B26" +
            "99C327186AF4E23C1A946834B6150BDA2583E9CA2AD44CE8DBBBC2DB" +
            "04DE8EF92E8EFC141FBECAA6287C59474E6BC05D99B2964FA090C3A2" +
            "233BA186515BE7ED1F612970CEE2D7AFB81BDD762170481CD0069127" +
            "D5B05AA993B4EA988D8FDDC186FFB7DC90A6C08F4DF435C934063199" +
            "FFFFFFFFFFFFFFFF",
            "5"
        ),
        // 8192-bit
        SRPNGType.NG_8192 to Pair(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E08" +
            "8A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B" +
            "302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9" +
            "A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE6" +
            "49286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8" +
            "FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
            "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3BE39E772C" +
            "180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF695581718" +
            "3995497CEA956AE515D2261898FA051015728E5A8AAAC42DAD33170D" +
            "04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7D" +
            "B3970F85A6E1E4C7ABF5AE8CDB0933D71E8C94E04A25619DCEE3D226" +
            "1AD2EE6BF12FFA06D98A0864D87602733EC86A64521F2B18177B200C" +
            "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB3143DB5BFC" +
            "E0FD108E4B82D120A92108011A723C12A787E6D788719A10BDBA5B26" +
            "99C327186AF4E23C1A946834B6150BDA2583E9CA2AD44CE8DBBBC2DB" +
            "04DE8EF92E8EFC141FBECAA6287C59474E6BC05D99B2964FA090C3A2" +
            "233BA186515BE7ED1F612970CEE2D7AFB81BDD762170481CD0069127" +
            "D5B05AA993B4EA988D8FDDC186FFB7DC90A6C08F4DF435C934028492" +
            "36C3FAB4D27C7026C1D4DCB2602646DEC9751E763DBA37BDF8FF9406" +
            "AD9E530EE5DB382F413001AEB06A53ED9027D831179727B0865A8918" +
            "DA3EDBEBCF9B14ED44CE6CBACED4BB1BDB7F1447E6CC254B33205151" +
            "2BD7AF426FB8F401378CD2BF5983CA01C64B92ECF032EA15D1721D03" +
            "F482D7CE6E74FEF6D55E702F46980C82B5A84031900B1C9E59E7C97F" +
            "BEC7E8F323A97A7E36CC88BE0F1D45B7FF585AC54BD407B22B4154AA" +
            "CC8F6D7EBF48E1D814CC5ED20F8037E0A79715EEF29BE32806A1D58B" +
            "B7C5DA76F550AA3D8A1FBFF0EB19CCB1A313D55CDA56C9EC2EF29632" +
            "387FE8D76E3C0468043E8F663F4860EE12BF2D5B0B7474D6E694F91E" +
            "6DBE115974A3926F12FEE5E438777CB6A932DF8CD8BEC4D073B931BA" +
            "3BC832B68D9DD300741FA7BF8AFC47ED2576F6936BA424663AAB639C" +
            "5AE4F5683423B4742BF1C978238F16CBE39D652DE3FDB8BEFC848AD9" +
            "22222E04A4037C0713EB57A81A23F0C73473FC646CEA306B4BCBC886" +
            "2F8385DDFA9D4B7FA2C087E879683303ED5BDD3A062B3CF5B3A278A6" +
            "6D2A13F83F44F82DDF310EE074AB6A364597E899A0255DC164F31CC5" +
            "0846851DF9AB48195DED7EA1B1D510BD7EE74D73FAF36BC31ECFA268" +
            "359046F4EB879F924009438B481C6CD7889A002ED5EE382BC9190DA6" +
            "FC026E479558E4475677E9AA9E3050E2765694DFC81F56E880B96E71" +
            "60C980DD98EDD3DFFFFFFFFFFFFFFFFF",
            "0x13"
        )
    )
    
    /**
     * 获取N和g常量
     */
    fun getNG(ngType: SRPNGType, nHex: String? = null, gHex: String? = null): Pair<BigInteger, BigInteger> {
        return when (ngType) {
            SRPNGType.NG_CUSTOM -> {
                require(nHex != null && gHex != null) { "Both nHex and gHex are required when ngType = NG_CUSTOM" }
                Pair(BigInteger(nHex, 16), BigInteger(gHex, 16))
            }
            else -> {
                val (n, g) = ngConstants[ngType]!!
                Pair(BigInteger(n, 16), BigInteger(g, 16))
            }
        }
    }
    
    /**
     * 字节数组转BigInteger
     */
    fun bytesToBigInt(bytes: ByteArray): BigInteger {
        return BigInteger(1, bytes)
    }
    
    /**
     * BigInteger转字节数组
     */
    fun bigIntToBytes(bigInt: BigInteger): ByteArray {
        val bytes = bigInt.toByteArray()
        // 移除前导零字节（如果有）
        return if (bytes[0] == 0.toByte() && bytes.size > 1) {
            bytes.copyOfRange(1, bytes.size)
        } else {
            bytes
        }
    }
    
    /**
     * 生成随机数
     */
    fun getRandom(nbytes: Int): BigInteger {
        val random = SecureRandom()
        val bytes = ByteArray(nbytes)
        random.nextBytes(bytes)
        return bytesToBigInt(bytes)
    }
    
    /**
     * 生成指定长度的随机数（确保最高位为1）
     */
    fun getRandomOfLength(nbytes: Int): BigInteger {
        val offset = (nbytes * 8) - 1
        return getRandom(nbytes).or(BigInteger.ONE.shiftLeft(offset))
    }
    
    /**
     * 哈希函数H()
     * 可变参数，支持BigInteger和ByteArray
     */
    fun H(hashAlg: SRPHashAlgorithm, vararg args: Any, width: Int? = null): ByteArray {
        val md = MessageDigest.getInstance(hashAlg.algorithmName)
        
        for (arg in args) {
            if (arg != null) {
                val data = when (arg) {
                    is BigInteger -> bigIntToBytes(arg)
                    is ByteArray -> arg
                    is String -> arg.toByteArray(Charsets.UTF_8)
                    else -> throw IllegalArgumentException("Unsupported type: ${arg::class.java}")
                }
                
                // RFC 5054 兼容性：添加填充
                if (width != null && config.rfc5054Compat && data.size < width) {
                    md.update(ByteArray(width - data.size))
                }
                
                md.update(data)
            }
        }
        
        return md.digest()
    }
    
    /**
     * 计算 H(N) xor H(g)
     */
    fun HNxorg(hashAlg: SRPHashAlgorithm, N: BigInteger, g: BigInteger): ByteArray {
        val binN = bigIntToBytes(N)
        val binG = bigIntToBytes(g)
        
        // RFC 5054 兼容性：g需要填充到与N相同长度
        val padding = if (config.rfc5054Compat) binN.size - binG.size else 0
        
        val mdN = MessageDigest.getInstance(hashAlg.algorithmName)
        val hN = mdN.digest(binN)
        
        val mdG = MessageDigest.getInstance(hashAlg.algorithmName)
        if (padding > 0) {
            mdG.update(ByteArray(padding))
        }
        mdG.update(binG)
        val hG = mdG.digest()
        
        // XOR操作
        val result = ByteArray(hN.size)
        for (i in hN.indices) {
            result[i] = hN[i] xor hG[i]
        }
        
        return result
    }
    
    /**
     * 计算私钥 x = H(s, H(I:p))
     */
    fun genX(hashAlg: SRPHashAlgorithm, salt: ByteArray, username: String, password: String): BigInteger {
        val usernameBytes = if (config.noUsernameInX) byteArrayOf() else username.toByteArray(Charsets.UTF_8)
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        
        // H(I:p)
        val innerHash = H(hashAlg, usernameBytes + ":".toByteArray(Charsets.UTF_8) + passwordBytes)
        
        // H(s, H(I:p))
        return bytesToBigInt(H(hashAlg, salt, innerHash))
    }
    
    /**
     * 创建盐值和验证器 (s, v)
     * v = g^x mod N, 其中 x = H(s, H(I:p))
     */
    fun createSaltedVerificationKey(
        username: String,
        password: String,
        hashAlg: SRPHashAlgorithm = SRPHashAlgorithm.SHA1,
        ngType: SRPNGType = SRPNGType.NG_2048,
        nHex: String? = null,
        gHex: String? = null,
        saltLen: Int = 4
    ): Pair<ByteArray, ByteArray> {
        if (ngType == SRPNGType.NG_CUSTOM) {
            require(nHex != null && gHex != null) { "Both nHex and gHex are required when ngType = NG_CUSTOM" }
        }
        
        val (N, g) = getNG(ngType, nHex, gHex)
        val s = bigIntToBytes(getRandom(saltLen))
        val x = genX(hashAlg, s, username, password)
        val v = g.modPow(x, N)
        
        return Pair(s, bigIntToBytes(v))
    }
    
    /**
     * 计算 M = H(H(N) xor H(g), H(I), s, A, B, K)
     */
    fun calculateM(
        hashAlg: SRPHashAlgorithm,
        N: BigInteger,
        g: BigInteger,
        I: String,
        s: ByteArray,
        A: BigInteger,
        B: BigInteger,
        K: ByteArray
    ): ByteArray {
        val md = MessageDigest.getInstance(hashAlg.algorithmName)
        md.update(HNxorg(hashAlg, N, g))
        md.update(H(hashAlg, I))
        md.update(s)
        md.update(bigIntToBytes(A))
        md.update(bigIntToBytes(B))
        md.update(K)
        return md.digest()
    }
    
    /**
     * 计算 H_AMK = H(A, M, K)
     */
    fun calculateHAMK(hashAlg: SRPHashAlgorithm, A: BigInteger, M: ByteArray, K: ByteArray): ByteArray {
        val md = MessageDigest.getInstance(hashAlg.algorithmName)
        md.update(bigIntToBytes(A))
        md.update(M)
        md.update(K)
        return md.digest()
    }
}

/**
 * SRP客户端 (User)
 */
class SRPUser(
    private val username: String,
    private val password: String,
    private val hashAlg: SRPHashAlgorithm = SRPHashAlgorithm.SHA256,
    private val ngType: SRPNGType = SRPNGType.NG_2048,
    private val nHex: String? = null,
    private val gHex: String? = null,
    bytesA: ByteArray? = null,
    kHex: String? = null
) {
    private val N: BigInteger
    private val g: BigInteger
    private val k: BigInteger
    private val a: BigInteger
    val A: BigInteger
    
    private var s: ByteArray? = null
    private var B: BigInteger? = null
    private var u: BigInteger? = null
    private var x: BigInteger? = null
    private var v: BigInteger? = null
    private var S: BigInteger? = null
    var K: ByteArray? = null
        private set
    var M: ByteArray? = null
        private set
    private var H_AMK: ByteArray? = null
    
    private var _authenticated = false
    val authenticated: Boolean
        get() = _authenticated
    
    init {
        if (ngType == SRPNGType.NG_CUSTOM) {
            require(nHex != null && gHex != null) { "Both nHex and gHex are required when ngType = NG_CUSTOM" }
        }
        
        val ng = SRPProtocol.getNG(ngType, nHex, gHex)
        N = ng.first
        g = ng.second
        
        // 计算 k = H(N, g)
        k = if (kHex != null) {
            BigInteger(kHex, 16)
        } else {
            SRPProtocol.bytesToBigInt(
                SRPProtocol.H(
                    hashAlg, 
                    N, 
                    g, 
                    width = SRPProtocol.bigIntToBytes(N).size
                )
            )
        }
        
        // 生成随机私钥 a 和公钥 A
        a = if (bytesA != null) {
            SRPProtocol.bytesToBigInt(bytesA)
        } else {
            SRPProtocol.getRandomOfLength(256)
        }
        
        A = g.modPow(a, N)
    }
    
    /**
     * 开始认证，返回 (I, A)
     */
    fun startAuthentication(): Pair<String, ByteArray> {
        return Pair(username, SRPProtocol.bigIntToBytes(A))
    }
    
    /**
     * 处理服务器挑战 (s, B)
     * 返回 M，如果SRP-6a安全检查失败则返回null
     */
    fun processChallenge(bytesS: ByteArray, bytesB: ByteArray): ByteArray? {
        s = bytesS
        B = SRPProtocol.bytesToBigInt(bytesB)
        
        // SRP-6a 安全检查: B % N != 0
        if (B!!.mod(N) == BigInteger.ZERO) {
            return null
        }
        
        // 计算 u = H(A, B)
        u = SRPProtocol.bytesToBigInt(
            SRPProtocol.H(
                hashAlg, 
                A, 
                B!!, 
                width = SRPProtocol.bigIntToBytes(N).size
            )
        )
        
        // SRP-6a 安全检查: u != 0
        if (u == BigInteger.ZERO) {
            return null
        }
        
        // 计算 x = H(s, H(I:p))
        x = SRPProtocol.genX(hashAlg, s!!, username, password)
        
        // 计算 v = g^x mod N
        v = g.modPow(x!!, N)
        
        // 计算 S = (B - k*v)^(a + u*x) mod N
        val exp = a.add(u!!.multiply(x!!))
        val base = B!!.subtract(k.multiply(v!!)).mod(N)
        S = base.modPow(exp, N)
        
        // 计算会话密钥 K = H(S)
        K = MessageDigest.getInstance(hashAlg.algorithmName)
            .digest(SRPProtocol.bigIntToBytes(S!!))
        
        // 计算 M = H(H(N) xor H(g), H(I), s, A, B, K)
        M = SRPProtocol.calculateM(hashAlg, N, g, username, s!!, A, B!!, K!!)
        
        // 计算 H_AMK = H(A, M, K)
        H_AMK = SRPProtocol.calculateHAMK(hashAlg, A, M!!, K!!)
        
        return M
    }
    
    /**
     * 验证服务器的响应
     */
    fun verifySession(hostHAMK: ByteArray): Boolean {
        if (H_AMK != null && H_AMK!!.contentEquals(hostHAMK)) {
            _authenticated = true
            return true
        }
        return false
    }
    
    /**
     * 获取会话密钥
     */
    fun getSessionKey(): ByteArray? {
        return if (_authenticated) K else null
    }
    
    /**
     * 获取临时私钥
     */
    fun getEphemeralSecret(): ByteArray {
        return SRPProtocol.bigIntToBytes(a)
    }
}

/**
 * SRP服务器 (Verifier)
 */
class SRPVerifier(
    private val username: String,
    private val bytesS: ByteArray,
    private val bytesV: ByteArray,
    bytesA: ByteArray? = null,
    private val hashAlg: SRPHashAlgorithm = SRPHashAlgorithm.SHA256,
    private val ngType: SRPNGType = SRPNGType.NG_2048,
    private val nHex: String? = null,
    private val gHex: String? = null,
    bytesB: ByteArray? = null,
    kHex: String? = null
) {
    private val N: BigInteger
    private val g: BigInteger
    private val k: BigInteger
    private val s: ByteArray = bytesS
    private val v: BigInteger
    private val b: BigInteger
    val B: BigInteger
    
    private var A: BigInteger? = null
    private var u: BigInteger? = null
    private var S: BigInteger? = null
    var K: ByteArray? = null
        private set
    private var M: ByteArray? = null
    private var H_AMK: ByteArray? = null
    
    var safetyFailed = false
        private set
    
    private var _authenticated = false
    val authenticated: Boolean
        get() = _authenticated
    
    init {
        if (ngType == SRPNGType.NG_CUSTOM) {
            require(nHex != null && gHex != null) { "Both nHex and gHex are required when ngType = NG_CUSTOM" }
        }
        
        require(bytesB == null || bytesB.size == 256) { "256 bytes required for bytesB" }
        
        val ng = SRPProtocol.getNG(ngType, nHex, gHex)
        N = ng.first
        g = ng.second
        v = SRPProtocol.bytesToBigInt(bytesV)
        
        // 计算 k = H(N, g)
        k = if (kHex != null) {
            BigInteger(kHex, 16)
        } else {
            SRPProtocol.bytesToBigInt(
                SRPProtocol.H(
                    hashAlg, 
                    N, 
                    g, 
                    width = SRPProtocol.bigIntToBytes(N).size
                )
            )
        }
        
        // 设置客户端公钥A
        if (bytesA != null) {
            setA(bytesA)
        }
        
        // 生成服务器私钥b和公钥B
        if (!safetyFailed) {
            b = if (bytesB != null) {
                SRPProtocol.bytesToBigInt(bytesB)
            } else {
                SRPProtocol.getRandomOfLength(256)
            }
            
            // B = k*v + g^b mod N
            B = k.multiply(v).add(g.modPow(b, N)).mod(N)
        } else {
            b = BigInteger.ZERO
            B = BigInteger.ZERO
        }
    }
    
    /**
     * 设置客户端公钥A
     */
    private fun setA(bytesA: ByteArray) {
        A = SRPProtocol.bytesToBigInt(bytesA)
        // SRP-6a 安全检查: A % N != 0
        safetyFailed = A!!.mod(N) == BigInteger.ZERO
    }
    
    /**
     * 获取挑战 (s, B)
     * 如果安全检查失败返回null
     */
    fun getChallenge(): Pair<ByteArray, ByteArray>? {
        return if (safetyFailed) {
            null
        } else {
            Pair(s, SRPProtocol.bigIntToBytes(B))
        }
    }
    
    /**
     * 验证客户端的M
     * 返回H_AMK或null
     */
    fun verifySession(userM: ByteArray, bytesA: ByteArray? = null): ByteArray? {
        if (bytesA != null) {
            setA(bytesA)
        }
        
        if (A == null) {
            throw IllegalStateException("bytesA must be provided through constructor or verifySession parameter")
        }
        
        if (!safetyFailed) {
            deriveHAMK()
            
            if (M != null && M!!.contentEquals(userM)) {
                _authenticated = true
                return H_AMK
            }
        }
        
        return null
    }
    
    /**
     * 计算会话密钥和验证值
     */
    private fun deriveHAMK() {
        // 计算 u = H(A, B)
        u = SRPProtocol.bytesToBigInt(
            SRPProtocol.H(
                hashAlg, 
                A!!, 
                B, 
                width = SRPProtocol.bigIntToBytes(N).size
            )
        )
        
        // 计算 S = (A * v^u)^b mod N
        S = A!!.multiply(v.modPow(u!!, N)).modPow(b, N)
        
        // 计算会话密钥 K = H(S)
        K = MessageDigest.getInstance(hashAlg.algorithmName)
            .digest(SRPProtocol.bigIntToBytes(S!!))
        
        // 计算 M = H(H(N) xor H(g), H(I), s, A, B, K)
        M = SRPProtocol.calculateM(hashAlg, N, g, username, s, A!!, B, K!!)
        
        // 计算 H_AMK = H(A, M, K)
        H_AMK = SRPProtocol.calculateHAMK(hashAlg, A!!, M!!, K!!)
    }
    
    /**
     * 获取会话密钥
     */
    fun getSessionKey(): ByteArray? {
        return if (_authenticated) K else null
    }
    
    /**
     * 获取临时私钥
     */
    fun getEphemeralSecret(): ByteArray {
        return SRPProtocol.bigIntToBytes(b)
    }
}
