OpenVPN Neo
=============
![build status](https://github.com/vogtmh/ics-openvpn-neo/actions/workflows/build.yaml/badge.svg)

Note to other developers 
------------------------
This is a spare time project that I work on my own time and pick to work what I want.
You are free use the source code of this app with the conditions (GPL) that are attached to it
but do not expect any support or anything that I do not feel like to provide. 

This project is a simple rewrite of "OpenVPN for Android", focusing on improving the user interface
in my own way. I use the app on a daily basis and there were a couple of things that I didn't like.
The visuals looked to much like Android 4.x (where it probably started), I missed consistency
throughout the app and some feature like the country display were simply not implemented. 

On the other hand, I don't care about device management and I do not need a minimal UI with a single
VPN connection to connect to. That's why I also removed a couple of things that simply did not fit 
my use case, which is: Adding VPN profiles from different countries and connecting to them.

The country bar is entirely optional. You are asked on first run if you'd like to enable it and you
can toggle it on or off through the settings. It will run a simple request to an open source api 
which looks trustworthy to me. It is not MY api, though. It do not own nor control it. The benefit
is simple: all your VPN profiles will get a flag attached after the first connection and your 
current country and public IP will be displayed at the top. No more manual lookups for your IP 
required.

If you build something on top of this is app you MUST publish your source code according to
the license of this app (GPL).


Description
------------
With the new VPNService of Android API level 14+ (Ice Cream Sandwich) it is possible to create a VPN service that does not need root access. This project is a port of OpenVPN.

<a href="https://play.google.com/store/apps/details?id=com.mavodev.openvpnneo" target="_blank">
<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on Google Play" height="80"/></a>


Note to administrators
------------------------

You make your life and that of your users easier if you embed the certificates into the .ovpn file. You or the users can mail the .ovpn as a attachment to the phone and directly import and use it. Also downloading and importing the file works. The MIME Type should be application/x-openvpn-profile. 

Inline files are supported since OpenVPN 2.1rc1 and documented in the  [OpenVPN 2.3 man page](https://community.openvpn.net/openvpn/wiki/Openvpn23ManPage) (under INLINE FILE SUPPORT) 

(Using inline certifaces can also make your life on non-Android platforms easier since you only have one file.)

For example `ca mycafile.pem` becomes
```
  <ca>
  -----BEGIN CERTIFICATE-----
  MIIHPTCCBSWgAwIBAgIBADANBgkqhkiG9w0BAQQFADB5MRAwDgYDVQQKEwdSb290
  [...]
  -----END CERTIFICATE-----
  </ca>
```

Footnotes
-----------
Please note that OpenVPN used by this project is under GPLv2. 

If you want to donate you can donate to [arne-paypal@rfc2549.org via paypal](https://www.paypal.com/cgi-bin/webscr?hosted_button_id=R2M6ZP9AF25LS&cmd=_s-xclick), or alternatively if you believe in fancy Internet money you can use Bitcoin: 1EVWVqpVQFhoFE6gKaqSkfvSNdmLAjcQ9z 

The Git repository is at GitHub under https://github.com/vogtmh/ics-openvpn-neo

Please read the doc/README before asking questions or starting development.
