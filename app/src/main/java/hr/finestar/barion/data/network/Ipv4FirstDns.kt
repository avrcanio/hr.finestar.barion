package hr.finestar.barion.data.network

import java.net.Inet4Address
import java.net.InetAddress
import okhttp3.Dns

class Ipv4FirstDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        return addresses.sortedBy { address ->
            if (address is Inet4Address) 0 else 1
        }
    }
}
