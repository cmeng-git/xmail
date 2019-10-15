package org.atalk.xryptomail.mail.internet;

import androidx.annotation.NonNull;

import org.apache.james.mime4j.codec.Base64InputStream;
import org.apache.james.mime4j.codec.QuotedPrintableInputStream;
import org.apache.james.mime4j.util.MimeUtil;
import org.atalk.xryptomail.mail.Body;
import org.atalk.xryptomail.mail.BodyPart;
import org.atalk.xryptomail.mail.Message;
import org.atalk.xryptomail.mail.MessagingException;
import org.atalk.xryptomail.mail.Multipart;
import org.atalk.xryptomail.mail.Part;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import timber.log.Timber;

public class MimeUtility
{
    public static final String DEFAULT_ATTACHMENT_MIME_TYPE = "application/octet-stream";
    public static final String XRYPTOMAIL_SETTINGS_MIME_TYPE = "application/x-XryptoMailsettings";
    /*
     * http://www.w3schools.com/media/media_mimeref.asp + http://www.stdicon.com/mimetypes
     */
    private static final String[][] MIME_TYPE_BY_EXTENSION_MAP = new String[][]{
            // * Do not delete the next three lines
            {"", DEFAULT_ATTACHMENT_MIME_TYPE},
            {"XryptoMail", XRYPTOMAIL_SETTINGS_MIME_TYPE},
            {"txt", "text/plain"},
            // * Do not delete the previous three lines
            {"123", "application/vnd.lotus-1-2-3"},
            {"323", "text/h323"},
            {"3dml", "text/vnd.in3d.3dml"},
            {"3g2", "video/3gpp2"},
            {"3gp", "video/3gpp"},
            {"aab", "application/x-authorware-bin"},
            {"aac", "audio/x-aac"},
            {"aam", "application/x-authorware-map"},
            {"a", "application/octet-stream"},
            {"aas", "application/x-authorware-seg"},
            {"abw", "application/x-abiword"},
            {"acc", "application/vnd.americandynamics.acc"},
            {"ace", "application/x-ace-compressed"},
            {"acu", "application/vnd.acucobol"},
            {"acutc", "application/vnd.acucorp"},
            {"acx", "application/internet-property-stream"},
            {"adp", "audio/adpcm"},
            {"aep", "application/vnd.audiograph"},
            {"afm", "application/x-font-type1"},
            {"afp", "application/vnd.ibm.modcap"},
            {"ai", "application/postscript"},
            {"aif", "audio/x-aiff"},
            {"aifc", "audio/x-aiff"},
            {"aiff", "audio/x-aiff"},
            {"air", "application/vnd.adobe.air-application-installer-package+zip"},
            {"ami", "application/vnd.amiga.ami"},
            {"apk", "application/vnd.android.package-archive"},
            {"application", "application/x-ms-application"},
            {"apr", "application/vnd.lotus-approach"},
            {"asc", "application/pgp-signature"},
            {"asf", "video/x-ms-asf"},
            {"asm", "text/x-asm"},
            {"aso", "application/vnd.accpac.simply.aso"},
            {"asr", "video/x-ms-asf"},
            {"asx", "video/x-ms-asf"},
            {"atc", "application/vnd.acucorp"},
            {"atom", "application/atom+xml"},
            {"atomcat", "application/atomcat+xml"},
            {"atomsvc", "application/atomsvc+xml"},
            {"atx", "application/vnd.antix.game-component"},
            {"au", "audio/basic"},
            {"avi", "video/x-msvideo"},
            {"aw", "application/applixware"},
            {"axs", "application/olescript"},
            {"azf", "application/vnd.airzip.filesecure.azf"},
            {"azs", "application/vnd.airzip.filesecure.azs"},
            {"azw", "application/vnd.amazon.ebook"},
            {"bas", "text/plain"},
            {"bat", "application/x-msdownload"},
            {"bcpio", "application/x-bcpio"},
            {"bdf", "application/x-font-bdf"},
            {"bdm", "application/vnd.syncml.dm+wbxml"},
            {"bh2", "application/vnd.fujitsu.oasysprs"},
            {"bin", "application/octet-stream"},
            {"bmi", "application/vnd.bmi"},
            {"bmp", "image/bmp"},
            {"book", "application/vnd.framemaker"},
            {"box", "application/vnd.previewsystems.box"},
            {"boz", "application/x-bzip2"},
            {"bpk", "application/octet-stream"},
            {"btif", "image/prs.btif"},
            {"bz2", "application/x-bzip2"},
            {"bz", "application/x-bzip"},
            {"c4d", "application/vnd.clonk.c4group"},
            {"c4f", "application/vnd.clonk.c4group"},
            {"c4g", "application/vnd.clonk.c4group"},
            {"c4p", "application/vnd.clonk.c4group"},
            {"c4u", "application/vnd.clonk.c4group"},
            {"cab", "application/vnd.ms-cab-compressed"},
            {"car", "application/vnd.curl.car"},
            {"cat", "application/vnd.ms-pki.seccat"},
            {"cct", "application/x-director"},
            {"cc", "text/x-c"},
            {"ccxml", "application/ccxml+xml"},
            {"cdbcmsg", "application/vnd.contact.cmsg"},
            {"cdf", "application/x-cdf"},
            {"cdkey", "application/vnd.mediastation.cdkey"},
            {"cdx", "chemical/x-cdx"},
            {"cdxml", "application/vnd.chemdraw+xml"},
            {"cdy", "application/vnd.cinderella"},
            {"cer", "application/x-x509-ca-cert"},
            {"cgm", "image/cgm"},
            {"chat", "application/x-chat"},
            {"chm", "application/vnd.ms-htmlhelp"},
            {"chrt", "application/vnd.kde.kchart"},
            {"cif", "chemical/x-cif"},
            {"cii", "application/vnd.anser-web-certificate-issue-initiation"},
            {"cla", "application/vnd.claymore"},
            {"class", "application/java-vm"},
            {"clkk", "application/vnd.crick.clicker.keyboard"},
            {"clkp", "application/vnd.crick.clicker.palette"},
            {"clkt", "application/vnd.crick.clicker.template"},
            {"clkw", "application/vnd.crick.clicker.wordbank"},
            {"clkx", "application/vnd.crick.clicker"},
            {"clp", "application/x-msclip"},
            {"cmc", "application/vnd.cosmocaller"},
            {"cmdf", "chemical/x-cmdf"},
            {"cml", "chemical/x-cml"},
            {"cmp", "application/vnd.yellowriver-custom-menu"},
            {"cmx", "image/x-cmx"},
            {"cod", "application/vnd.rim.cod"},
            {"com", "application/x-msdownload"},
            {"conf", "text/plain"},
            {"cpio", "application/x-cpio"},
            {"cpp", "text/x-c"},
            {"cpt", "application/mac-compactpro"},
            {"crd", "application/x-mscardfile"},
            {"crl", "application/pkix-crl"},
            {"crt", "application/x-x509-ca-cert"},
            {"csh", "application/x-csh"},
            {"csml", "chemical/x-csml"},
            {"csp", "application/vnd.commonspace"},
            {"css", "text/css"},
            {"cst", "application/x-director"},
            {"csv", "text/csv"},
            {"c", "text/plain"},
            {"cu", "application/cu-seeme"},
            {"curl", "text/vnd.curl"},
            {"cww", "application/prs.cww"},
            {"cxt", "application/x-director"},
            {"cxx", "text/x-c"},
            {"daf", "application/vnd.mobius.daf"},
            {"dataless", "application/vnd.fdsn.seed"},
            {"davmount", "application/davmount+xml"},
            {"dcr", "application/x-director"},
            {"dcurl", "text/vnd.curl.dcurl"},
            {"dd2", "application/vnd.oma.dd2+xml"},
            {"ddd", "application/vnd.fujixerox.ddd"},
            {"deb", "application/x-debian-package"},
            {"def", "text/plain"},
            {"deploy", "application/octet-stream"},
            {"der", "application/x-x509-ca-cert"},
            {"dfac", "application/vnd.dreamfactory"},
            {"dic", "text/x-c"},
            {"diff", "text/plain"},
            {"dir", "application/x-director"},
            {"dis", "application/vnd.mobius.dis"},
            {"dist", "application/octet-stream"},
            {"distz", "application/octet-stream"},
            {"djv", "image/vnd.djvu"},
            {"djvu", "image/vnd.djvu"},
            {"dll", "application/x-msdownload"},
            {"dmg", "application/octet-stream"},
            {"dms", "application/octet-stream"},
            {"dna", "application/vnd.dna"},
            {"doc", "application/msword"},
            {"docm", "application/vnd.ms-word.document.macroenabled.12"},
            {"docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
            {"dot", "application/msword"},
            {"dotm", "application/vnd.ms-word.template.macroenabled.12"},
            {"dotx", "application/vnd.openxmlformats-officedocument.wordprocessingml.template"},
            {"dp", "application/vnd.osgi.dp"},
            {"dpg", "application/vnd.dpgraph"},
            {"dsc", "text/prs.lines.tag"},
            {"dtb", "application/x-dtbook+xml"},
            {"dtd", "application/xml-dtd"},
            {"dts", "audio/vnd.dts"},
            {"dtshd", "audio/vnd.dts.hd"},
            {"dump", "application/octet-stream"},
            {"dvi", "application/x-dvi"},
            {"dwf", "model/vnd.dwf"},
            {"dwg", "image/vnd.dwg"},
            {"dxf", "image/vnd.dxf"},
            {"dxp", "application/vnd.spotfire.dxp"},
            {"dxr", "application/x-director"},
            {"ecelp4800", "audio/vnd.nuera.ecelp4800"},
            {"ecelp7470", "audio/vnd.nuera.ecelp7470"},
            {"ecelp9600", "audio/vnd.nuera.ecelp9600"},
            {"ecma", "application/ecmascript"},
            {"edm", "application/vnd.novadigm.edm"},
            {"edx", "application/vnd.novadigm.edx"},
            {"efif", "application/vnd.picsel"},
            {"ei6", "application/vnd.pg.osasli"},
            {"elc", "application/octet-stream"},
            {"eml", "message/rfc822"},
            {"emma", "application/emma+xml"},
            {"eol", "audio/vnd.digital-winds"},
            {"eot", "application/vnd.ms-fontobject"},
            {"eps", "application/postscript"},
            {"epub", "application/epub+zip"},
            {"es3", "application/vnd.eszigno3+xml"},
            {"esf", "application/vnd.epson.esf"},
            {"espass", "application/vnd.espass-espass+zip"},
            {"et3", "application/vnd.eszigno3+xml"},
            {"etx", "text/x-setext"},
            {"evy", "application/envoy"},
            {"exe", "application/octet-stream"},
            {"ext", "application/vnd.novadigm.ext"},
            {"ez2", "application/vnd.ezpix-album"},
            {"ez3", "application/vnd.ezpix-package"},
            {"ez", "application/andrew-inset"},
            {"f4v", "video/x-f4v"},
            {"f77", "text/x-fortran"},
            {"f90", "text/x-fortran"},
            {"fbs", "image/vnd.fastbidsheet"},
            {"fdf", "application/vnd.fdf"},
            {"fe_launch", "application/vnd.denovo.fcselayout-link"},
            {"fg5", "application/vnd.fujitsu.oasysgp"},
            {"fgd", "application/x-director"},
            {"fh4", "image/x-freehand"},
            {"fh5", "image/x-freehand"},
            {"fh7", "image/x-freehand"},
            {"fhc", "image/x-freehand"},
            {"fh", "image/x-freehand"},
            {"fif", "application/fractals"},
            {"fig", "application/x-xfig"},
            {"fli", "video/x-fli"},
            {"flo", "application/vnd.micrografx.flo"},
            {"flr", "x-world/x-vrml"},
            {"flv", "video/x-flv"},
            {"flw", "application/vnd.kde.kivio"},
            {"flx", "text/vnd.fmi.flexstor"},
            {"fly", "text/vnd.fly"},
            {"fm", "application/vnd.framemaker"},
            {"fnc", "application/vnd.frogans.fnc"},
            {"for", "text/x-fortran"},
            {"fpx", "image/vnd.fpx"},
            {"frame", "application/vnd.framemaker"},
            {"fsc", "application/vnd.fsc.weblaunch"},
            {"fst", "image/vnd.fst"},
            {"ftc", "application/vnd.fluxtime.clip"},
            {"f", "text/x-fortran"},
            {"fti", "application/vnd.anser-web-funds-transfer-initiation"},
            {"fvt", "video/vnd.fvt"},
            {"fzs", "application/vnd.fuzzysheet"},
            {"g3", "image/g3fax"},
            {"gac", "application/vnd.groove-account"},
            {"gdl", "model/vnd.gdl"},
            {"geo", "application/vnd.dynageo"},
            {"gex", "application/vnd.geometry-explorer"},
            {"ggb", "application/vnd.geogebra.file"},
            {"ggt", "application/vnd.geogebra.tool"},
            {"ghf", "application/vnd.groove-help"},
            {"gif", "image/gif"},
            {"gim", "application/vnd.groove-identity-message"},
            {"gmx", "application/vnd.gmx"},
            {"gnumeric", "application/x-gnumeric"},
            {"gph", "application/vnd.flographit"},
            {"gqf", "application/vnd.grafeq"},
            {"gqs", "application/vnd.grafeq"},
            {"gram", "application/srgs"},
            {"gre", "application/vnd.geometry-explorer"},
            {"grv", "application/vnd.groove-injector"},
            {"grxml", "application/srgs+xml"},
            {"gsf", "application/x-font-ghostscript"},
            {"gtar", "application/x-gtar"},
            {"gtm", "application/vnd.groove-tool-message"},
            {"gtw", "model/vnd.gtw"},
            {"gv", "text/vnd.graphviz"},
            {"gz", "application/x-gzip"},
            {"h261", "video/h261"},
            {"h263", "video/h263"},
            {"h264", "video/h264"},
            {"hbci", "application/vnd.hbci"},
            {"hdf", "application/x-hdf"},
            {"hh", "text/x-c"},
            {"hlp", "application/winhlp"},
            {"hpgl", "application/vnd.hp-hpgl"},
            {"hpid", "application/vnd.hp-hpid"},
            {"hps", "application/vnd.hp-hps"},
            {"hqx", "application/mac-binhex40"},
            {"hta", "application/hta"},
            {"htc", "text/x-component"},
            {"h", "text/plain"},
            {"htke", "application/vnd.kenameaapp"},
            {"html", "text/html"},
            {"htm", "text/html"},
            {"htt", "text/webviewhtml"},
            {"hvd", "application/vnd.yamaha.hv-dic"},
            {"hvp", "application/vnd.yamaha.hv-voice"},
            {"hvs", "application/vnd.yamaha.hv-script"},
            {"icc", "application/vnd.iccprofile"},
            {"ice", "x-conference/x-cooltalk"},
            {"icm", "application/vnd.iccprofile"},
            {"ico", "image/x-icon"},
            {"ics", "text/calendar"},
            {"ief", "image/ief"},
            {"ifb", "text/calendar"},
            {"ifm", "application/vnd.shana.informed.formdata"},
            {"iges", "model/iges"},
            {"igl", "application/vnd.igloader"},
            {"igs", "model/iges"},
            {"igx", "application/vnd.micrografx.igx"},
            {"iif", "application/vnd.shana.informed.interchange"},
            {"iii", "application/x-iphone"},
            {"imp", "application/vnd.accpac.simply.imp"},
            {"ims", "application/vnd.ms-ims"},
            {"ins", "application/x-internet-signup"},
            {"in", "text/plain"},
            {"ipk", "application/vnd.shana.informed.package"},
            {"irm", "application/vnd.ibm.rights-management"},
            {"irp", "application/vnd.irepository.package+xml"},
            {"iso", "application/octet-stream"},
            {"isp", "application/x-internet-signup"},
            {"itp", "application/vnd.shana.informed.formtemplate"},
            {"ivp", "application/vnd.immervision-ivp"},
            {"ivu", "application/vnd.immervision-ivu"},
            {"jad", "text/vnd.sun.j2me.app-descriptor"},
            {"jam", "application/vnd.jam"},
            {"jar", "application/java-archive"},
            {"java", "text/x-java-source"},
            {"jfif", "image/pipeg"},
            {"jisp", "application/vnd.jisp"},
            {"jlt", "application/vnd.hp-jlyt"},
            {"jnlp", "application/x-java-jnlp-file"},
            {"joda", "application/vnd.joost.joda-archive"},
            {"jpeg", "image/jpeg"},
            {"jpe", "image/jpeg"},
            {"jpg", "image/jpeg"},
            {"jpgm", "video/jpm"},
            {"jpgv", "video/jpeg"},
            {"jpm", "video/jpm"},
            {"js", "application/x-javascript"},
            {"json", "application/json"},
            {"kar", "audio/midi"},
            {"karbon", "application/vnd.kde.karbon"},
            {"kfo", "application/vnd.kde.kformula"},
            {"kia", "application/vnd.kidspiration"},
            {"kil", "application/x-killustrator"},
            {"kml", "application/vnd.google-earth.kml+xml"},
            {"kmz", "application/vnd.google-earth.kmz"},
            {"kne", "application/vnd.kinar"},
            {"knp", "application/vnd.kinar"},
            {"kon", "application/vnd.kde.kontour"},
            {"kpr", "application/vnd.kde.kpresenter"},
            {"kpt", "application/vnd.kde.kpresenter"},
            {"ksh", "text/plain"},
            {"ksp", "application/vnd.kde.kspread"},
            {"ktr", "application/vnd.kahootz"},
            {"ktz", "application/vnd.kahootz"},
            {"kwd", "application/vnd.kde.kword"},
            {"kwt", "application/vnd.kde.kword"},
            {"latex", "application/x-latex"},
            {"lbd", "application/vnd.llamagraphics.life-balance.desktop"},
            {"lbe", "application/vnd.llamagraphics.life-balance.exchange+xml"},
            {"les", "application/vnd.hhe.lesson-player"},
            {"lha", "application/octet-stream"},
            {"link66", "application/vnd.route66.link66+xml"},
            {"list3820", "application/vnd.ibm.modcap"},
            {"listafp", "application/vnd.ibm.modcap"},
            {"list", "text/plain"},
            {"log", "text/plain"},
            {"lostxml", "application/lost+xml"},
            {"lrf", "application/octet-stream"},
            {"lrm", "application/vnd.ms-lrm"},
            {"lsf", "video/x-la-asf"},
            {"lsx", "video/x-la-asf"},
            {"ltf", "application/vnd.frogans.ltf"},
            {"lvp", "audio/vnd.lucent.voice"},
            {"lwp", "application/vnd.lotus-wordpro"},
            {"lzh", "application/octet-stream"},
            {"m13", "application/x-msmediaview"},
            {"m14", "application/x-msmediaview"},
            {"m1v", "video/mpeg"},
            {"m2a", "audio/mpeg"},
            {"m2v", "video/mpeg"},
            {"m3a", "audio/mpeg"},
            {"m3u", "audio/x-mpegurl"},
            {"m4u", "video/vnd.mpegurl"},
            {"m4v", "video/x-m4v"},
            {"ma", "application/mathematica"},
            {"mag", "application/vnd.ecowin.chart"},
            {"maker", "application/vnd.framemaker"},
            {"man", "text/troff"},
            {"mathml", "application/mathml+xml"},
            {"mb", "application/mathematica"},
            {"mbk", "application/vnd.mobius.mbk"},
            {"mbox", "application/mbox"},
            {"mc1", "application/vnd.medcalcdata"},
            {"mcd", "application/vnd.mcd"},
            {"mcurl", "text/vnd.curl.mcurl"},
            {"mdb", "application/x-msaccess"},
            {"mdi", "image/vnd.ms-modi"},
            {"mesh", "model/mesh"},
            {"me", "text/troff"},
            {"mfm", "application/vnd.mfmp"},
            {"mgz", "application/vnd.proteus.magazine"},
            {"mht", "message/rfc822"},
            {"mhtml", "message/rfc822"},
            {"mid", "audio/midi"},
            {"midi", "audio/midi"},
            {"mif", "application/vnd.mif"},
            {"mime", "message/rfc822"},
            {"mj2", "video/mj2"},
            {"mjp2", "video/mj2"},
            {"mlp", "application/vnd.dolby.mlp"},
            {"mmd", "application/vnd.chipnuts.karaoke-mmd"},
            {"mmf", "application/vnd.smaf"},
            {"mmr", "image/vnd.fujixerox.edmics-mmr"},
            {"mny", "application/x-msmoney"},
            {"mobi", "application/x-mobipocket-ebook"},
            {"movie", "video/x-sgi-movie"},
            {"mov", "video/quicktime"},
            {"mp2a", "audio/mpeg"},
            {"mp2", "video/mpeg"},
            {"mp3", "audio/mpeg"},
            {"mp4a", "audio/mp4"},
            {"mp4s", "application/mp4"},
            {"mp4", "video/mp4"},
            {"mp4v", "video/mp4"},
            {"mpa", "video/mpeg"},
            {"mpc", "application/vnd.mophun.certificate"},
            {"mpeg", "video/mpeg"},
            {"mpe", "video/mpeg"},
            {"mpg4", "video/mp4"},
            {"mpga", "audio/mpeg"},
            {"mpg", "video/mpeg"},
            {"mpkg", "application/vnd.apple.installer+xml"},
            {"mpm", "application/vnd.blueice.multipass"},
            {"mpn", "application/vnd.mophun.application"},
            {"mpp", "application/vnd.ms-project"},
            {"mpt", "application/vnd.ms-project"},
            {"mpv2", "video/mpeg"},
            {"mpy", "application/vnd.ibm.minipay"},
            {"mqy", "application/vnd.mobius.mqy"},
            {"mrc", "application/marc"},
            {"mscml", "application/mediaservercontrol+xml"},
            {"mseed", "application/vnd.fdsn.mseed"},
            {"mseq", "application/vnd.mseq"},
            {"msf", "application/vnd.epson.msf"},
            {"msh", "model/mesh"},
            {"msi", "application/x-msdownload"},
            {"ms", "text/troff"},
            {"msty", "application/vnd.muvee.style"},
            {"mts", "model/vnd.mts"},
            {"mus", "application/vnd.musician"},
            {"musicxml", "application/vnd.recordare.musicxml+xml"},
            {"mvb", "application/x-msmediaview"},
            {"mxf", "application/mxf"},
            {"mxl", "application/vnd.recordare.musicxml"},
            {"mxml", "application/xv+xml"},
            {"mxs", "application/vnd.triscape.mxs"},
            {"mxu", "video/vnd.mpegurl"},
            {"nb", "application/mathematica"},
            {"nc", "application/x-netcdf"},
            {"ncx", "application/x-dtbncx+xml"},
            {"n-gage", "application/vnd.nokia.n-gage.symbian.install"},
            {"ngdat", "application/vnd.nokia.n-gage.data"},
            {"nlu", "application/vnd.neurolanguage.nlu"},
            {"nml", "application/vnd.enliven"},
            {"nnd", "application/vnd.noblenet-directory"},
            {"nns", "application/vnd.noblenet-sealer"},
            {"nnw", "application/vnd.noblenet-web"},
            {"npx", "image/vnd.net-fpx"},
            {"nsf", "application/vnd.lotus-notes"},
            {"nws", "message/rfc822"},
            {"oa2", "application/vnd.fujitsu.oasys2"},
            {"oa3", "application/vnd.fujitsu.oasys3"},
            {"o", "application/octet-stream"},
            {"oas", "application/vnd.fujitsu.oasys"},
            {"obd", "application/x-msbinder"},
            {"obj", "application/octet-stream"},
            {"oda", "application/oda"},
            {"odb", "application/vnd.oasis.opendocument.database"},
            {"odc", "application/vnd.oasis.opendocument.chart"},
            {"odf", "application/vnd.oasis.opendocument.formula"},
            {"odft", "application/vnd.oasis.opendocument.formula-template"},
            {"odg", "application/vnd.oasis.opendocument.graphics"},
            {"odi", "application/vnd.oasis.opendocument.image"},
            {"odp", "application/vnd.oasis.opendocument.presentation"},
            {"ods", "application/vnd.oasis.opendocument.spreadsheet"},
            {"odt", "application/vnd.oasis.opendocument.text"},
            {"oga", "audio/ogg"},
            {"ogg", "audio/ogg"},
            {"ogv", "video/ogg"},
            {"ogx", "application/ogg"},
            {"onepkg", "application/onenote"},
            {"onetmp", "application/onenote"},
            {"onetoc2", "application/onenote"},
            {"onetoc", "application/onenote"},
            {"opf", "application/oebps-package+xml"},
            {"oprc", "application/vnd.palm"},
            {"org", "application/vnd.lotus-organizer"},
            {"osf", "application/vnd.yamaha.openscoreformat"},
            {"osfpvg", "application/vnd.yamaha.openscoreformat.osfpvg+xml"},
            {"otc", "application/vnd.oasis.opendocument.chart-template"},
            {"otf", "application/x-font-otf"},
            {"otg", "application/vnd.oasis.opendocument.graphics-template"},
            {"oth", "application/vnd.oasis.opendocument.text-web"},
            {"oti", "application/vnd.oasis.opendocument.image-template"},
            {"otm", "application/vnd.oasis.opendocument.text-master"},
            {"otp", "application/vnd.oasis.opendocument.presentation-template"},
            {"ots", "application/vnd.oasis.opendocument.spreadsheet-template"},
            {"ott", "application/vnd.oasis.opendocument.text-template"},
            {"oxt", "application/vnd.openofficeorg.extension"},
            {"p10", "application/pkcs10"},
            {"p12", "application/x-pkcs12"},
            {"p7b", "application/x-pkcs7-certificates"},
            {"p7c", "application/x-pkcs7-mime"},
            {"p7m", "application/x-pkcs7-mime"},
            {"p7r", "application/x-pkcs7-certreqresp"},
            {"p7s", "application/x-pkcs7-signature"},
            {"pas", "text/x-pascal"},
            {"pbd", "application/vnd.powerbuilder6"},
            {"pbm", "image/x-portable-bitmap"},
            {"pcf", "application/x-font-pcf"},
            {"pcl", "application/vnd.hp-pcl"},
            {"pclxl", "application/vnd.hp-pclxl"},
            {"pct", "image/x-pict"},
            {"pcurl", "application/vnd.curl.pcurl"},
            {"pcx", "image/x-pcx"},
            {"pdb", "application/vnd.palm"},
            {"pdf", "application/pdf"},
            {"pfa", "application/x-font-type1"},
            {"pfb", "application/x-font-type1"},
            {"pfm", "application/x-font-type1"},
            {"pfr", "application/font-tdpfr"},
            {"pfx", "application/x-pkcs12"},
            {"pgm", "image/x-portable-graymap"},
            {"pgn", "application/x-chess-pgn"},
            {"pgp", "application/pgp-encrypted"},
            {"pic", "image/x-pict"},
            {"pkg", "application/octet-stream"},
            {"pki", "application/pkixcmp"},
            {"pkipath", "application/pkix-pkipath"},
            {"pkpass", "application/vnd-com.apple.pkpass"},
            {"pko", "application/ynd.ms-pkipko"},
            {"plb", "application/vnd.3gpp.pic-bw-large"},
            {"plc", "application/vnd.mobius.plc"},
            {"plf", "application/vnd.pocketlearn"},
            {"pls", "application/pls+xml"},
            {"pl", "text/plain"},
            {"pma", "application/x-perfmon"},
            {"pmc", "application/x-perfmon"},
            {"pml", "application/x-perfmon"},
            {"pmr", "application/x-perfmon"},
            {"pmw", "application/x-perfmon"},
            {"png", "image/png"},
            {"pnm", "image/x-portable-anymap"},
            {"portpkg", "application/vnd.macports.portpkg"},
            {"pot,", "application/vnd.ms-powerpoint"},
            {"pot", "application/vnd.ms-powerpoint"},
            {"potm", "application/vnd.ms-powerpoint.template.macroenabled.12"},
            {"potx", "application/vnd.openxmlformats-officedocument.presentationml.template"},
            {"ppa", "application/vnd.ms-powerpoint"},
            {"ppam", "application/vnd.ms-powerpoint.addin.macroenabled.12"},
            {"ppd", "application/vnd.cups-ppd"},
            {"ppm", "image/x-portable-pixmap"},
            {"pps", "application/vnd.ms-powerpoint"},
            {"ppsm", "application/vnd.ms-powerpoint.slideshow.macroenabled.12"},
            {"ppsx", "application/vnd.openxmlformats-officedocument.presentationml.slideshow"},
            {"ppt", "application/vnd.ms-powerpoint"},
            {"pptm", "application/vnd.ms-powerpoint.presentation.macroenabled.12"},
            {"pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"},
            {"pqa", "application/vnd.palm"},
            {"prc", "application/x-mobipocket-ebook"},
            {"pre", "application/vnd.lotus-freelance"},
            {"prf", "application/pics-rules"},
            {"ps", "application/postscript"},
            {"psb", "application/vnd.3gpp.pic-bw-small"},
            {"psd", "image/vnd.adobe.photoshop"},
            {"psf", "application/x-font-linux-psf"},
            {"p", "text/x-pascal"},
            {"ptid", "application/vnd.pvi.ptid1"},
            {"pub", "application/x-mspublisher"},
            {"pvb", "application/vnd.3gpp.pic-bw-var"},
            {"pwn", "application/vnd.3m.post-it-notes"},
            {"pwz", "application/vnd.ms-powerpoint"},
            {"pya", "audio/vnd.ms-playready.media.pya"},
            {"pyc", "application/x-python-code"},
            {"pyo", "application/x-python-code"},
            {"py", "text/x-python"},
            {"pyv", "video/vnd.ms-playready.media.pyv"},
            {"qam", "application/vnd.epson.quickanime"},
            {"qbo", "application/vnd.intu.qbo"},
            {"qfx", "application/vnd.intu.qfx"},
            {"qps", "application/vnd.publishare-delta-tree"},
            {"qt", "video/quicktime"},
            {"qwd", "application/vnd.quark.quarkxpress"},
            {"qwt", "application/vnd.quark.quarkxpress"},
            {"qxb", "application/vnd.quark.quarkxpress"},
            {"qxd", "application/vnd.quark.quarkxpress"},
            {"qxl", "application/vnd.quark.quarkxpress"},
            {"qxt", "application/vnd.quark.quarkxpress"},
            {"ra", "audio/x-pn-realaudio"},
            {"ram", "audio/x-pn-realaudio"},
            {"rar", "application/x-rar-compressed"},
            {"ras", "image/x-cmu-raster"},
            {"rcprofile", "application/vnd.ipunplugged.rcprofile"},
            {"rdf", "application/rdf+xml"},
            {"rdz", "application/vnd.data-vision.rdz"},
            {"rep", "application/vnd.businessobjects"},
            {"res", "application/x-dtbresource+xml"},
            {"rgb", "image/x-rgb"},
            {"rif", "application/reginfo+xml"},
            {"rl", "application/resource-lists+xml"},
            {"rlc", "image/vnd.fujixerox.edmics-rlc"},
            {"rld", "application/resource-lists-diff+xml"},
            {"rm", "application/vnd.rn-realmedia"},
            {"rmi", "audio/midi"},
            {"rmp", "audio/x-pn-realaudio-plugin"},
            {"rms", "application/vnd.jcp.javame.midlet-rms"},
            {"rnc", "application/relax-ng-compact-syntax"},
            {"roff", "text/troff"},
            {"rpm", "application/x-rpm"},
            {"rpss", "application/vnd.nokia.radio-presets"},
            {"rpst", "application/vnd.nokia.radio-preset"},
            {"rq", "application/sparql-query"},
            {"rs", "application/rls-services+xml"},
            {"rsd", "application/rsd+xml"},
            {"rss", "application/rss+xml"},
            {"rtf", "application/rtf"},
            {"rtx", "text/richtext"},
            {"saf", "application/vnd.yamaha.smaf-audio"},
            {"sbml", "application/sbml+xml"},
            {"sc", "application/vnd.ibm.secure-container"},
            {"scd", "application/x-msschedule"},
            {"scm", "application/vnd.lotus-screencam"},
            {"scq", "application/scvp-cv-request"},
            {"scs", "application/scvp-cv-response"},
            {"sct", "text/scriptlet"},
            {"scurl", "text/vnd.curl.scurl"},
            {"sda", "application/vnd.stardivision.draw"},
            {"sdc", "application/vnd.stardivision.calc"},
            {"sdd", "application/vnd.stardivision.impress"},
            {"sdkd", "application/vnd.solent.sdkm+xml"},
            {"sdkm", "application/vnd.solent.sdkm+xml"},
            {"sdp", "application/sdp"},
            {"sdw", "application/vnd.stardivision.writer"},
            {"see", "application/vnd.seemail"},
            {"seed", "application/vnd.fdsn.seed"},
            {"sema", "application/vnd.sema"},
            {"semd", "application/vnd.semd"},
            {"semf", "application/vnd.semf"},
            {"ser", "application/java-serialized-object"},
            {"setpay", "application/set-payment-initiation"},
            {"setreg", "application/set-registration-initiation"},
            {"sfd-hdstx", "application/vnd.hydrostatix.sof-data"},
            {"sfs", "application/vnd.spotfire.sfs"},
            {"sgl", "application/vnd.stardivision.writer-global"},
            {"sgml", "text/sgml"},
            {"sgm", "text/sgml"},
            {"sh", "application/x-sh"},
            {"shar", "application/x-shar"},
            {"shf", "application/shf+xml"},
            {"sic", "application/vnd.wap.sic"},
            {"sig", "application/pgp-signature"},
            {"silo", "model/mesh"},
            {"sis", "application/vnd.symbian.install"},
            {"sisx", "application/vnd.symbian.install"},
            {"sit", "application/x-stuffit"},
            {"si", "text/vnd.wap.si"},
            {"sitx", "application/x-stuffitx"},
            {"skd", "application/vnd.koan"},
            {"skm", "application/vnd.koan"},
            {"skp", "application/vnd.koan"},
            {"skt", "application/vnd.koan"},
            {"slc", "application/vnd.wap.slc"},
            {"sldm", "application/vnd.ms-powerpoint.slide.macroenabled.12"},
            {"sldx", "application/vnd.openxmlformats-officedocument.presentationml.slide"},
            {"slt", "application/vnd.epson.salt"},
            {"sl", "text/vnd.wap.sl"},
            {"smf", "application/vnd.stardivision.math"},
            {"smi", "application/smil+xml"},
            {"smil", "application/smil+xml"},
            {"snd", "audio/basic"},
            {"snf", "application/x-font-snf"},
            {"so", "application/octet-stream"},
            {"spc", "application/x-pkcs7-certificates"},
            {"spf", "application/vnd.yamaha.smaf-phrase"},
            {"spl", "application/x-futuresplash"},
            {"spot", "text/vnd.in3d.spot"},
            {"spp", "application/scvp-vp-response"},
            {"spq", "application/scvp-vp-request"},
            {"spx", "audio/ogg"},
            {"src", "application/x-wais-source"},
            {"srx", "application/sparql-results+xml"},
            {"sse", "application/vnd.kodak-descriptor"},
            {"ssf", "application/vnd.epson.ssf"},
            {"ssml", "application/ssml+xml"},
            {"sst", "application/vnd.ms-pkicertstore"},
            {"stc", "application/vnd.sun.xml.calc.template"},
            {"std", "application/vnd.sun.xml.draw.template"},
            {"s", "text/x-asm"},
            {"stf", "application/vnd.wt.stf"},
            {"sti", "application/vnd.sun.xml.impress.template"},
            {"stk", "application/hyperstudio"},
            {"stl", "application/vnd.ms-pki.stl"},
            {"stm", "text/html"},
            {"str", "application/vnd.pg.format"},
            {"stw", "application/vnd.sun.xml.writer.template"},
            {"sus", "application/vnd.sus-calendar"},
            {"susp", "application/vnd.sus-calendar"},
            {"sv4cpio", "application/x-sv4cpio"},
            {"sv4crc", "application/x-sv4crc"},
            {"svd", "application/vnd.svd"},
            {"svg", "image/svg+xml"},
            {"svgz", "image/svg+xml"},
            {"swa", "application/x-director"},
            {"swf", "application/x-shockwave-flash"},
            {"swi", "application/vnd.arastra.swi"},
            {"sxc", "application/vnd.sun.xml.calc"},
            {"sxd", "application/vnd.sun.xml.draw"},
            {"sxg", "application/vnd.sun.xml.writer.global"},
            {"sxi", "application/vnd.sun.xml.impress"},
            {"sxm", "application/vnd.sun.xml.math"},
            {"sxw", "application/vnd.sun.xml.writer"},
            {"tao", "application/vnd.tao.intent-module-archive"},
            {"t", "application/x-troff"},
            {"tar", "application/x-tar"},
            {"tcap", "application/vnd.3gpp2.tcap"},
            {"tcl", "application/x-tcl"},
            {"teacher", "application/vnd.smart.teacher"},
            {"tex", "application/x-tex"},
            {"texi", "application/x-texinfo"},
            {"texinfo", "application/x-texinfo"},
            {"text", "text/plain"},
            {"tfm", "application/x-tex-tfm"},
            {"tgz", "application/x-gzip"},
            {"tiff", "image/tiff"},
            {"tif", "image/tiff"},
            {"tmo", "application/vnd.tmobile-livetv"},
            {"torrent", "application/x-bittorrent"},
            {"tpl", "application/vnd.groove-tool-template"},
            {"tpt", "application/vnd.trid.tpt"},
            {"tra", "application/vnd.trueapp"},
            {"trm", "application/x-msterminal"},
            {"tr", "text/troff"},
            {"tsv", "text/tab-separated-values"},
            {"ttc", "application/x-font-ttf"},
            {"ttf", "application/x-font-ttf"},
            {"twd", "application/vnd.simtech-mindmapper"},
            {"twds", "application/vnd.simtech-mindmapper"},
            {"txd", "application/vnd.genomatix.tuxedo"},
            {"txf", "application/vnd.mobius.txf"},
            {"txt", "text/plain"},
            {"u32", "application/x-authorware-bin"},
            {"udeb", "application/x-debian-package"},
            {"ufd", "application/vnd.ufdl"},
            {"ufdl", "application/vnd.ufdl"},
            {"uls", "text/iuls"},
            {"umj", "application/vnd.umajin"},
            {"unityweb", "application/vnd.unity"},
            {"uoml", "application/vnd.uoml+xml"},
            {"uris", "text/uri-list"},
            {"uri", "text/uri-list"},
            {"urls", "text/uri-list"},
            {"ustar", "application/x-ustar"},
            {"utz", "application/vnd.uiq.theme"},
            {"uu", "text/x-uuencode"},
            {"vcd", "application/x-cdlink"},
            {"vcf", "text/x-vcard"},
            {"vcg", "application/vnd.groove-vcard"},
            {"vcs", "text/x-vcalendar"},
            {"vcx", "application/vnd.vcx"},
            {"vis", "application/vnd.visionary"},
            {"viv", "video/vnd.vivo"},
            {"vor", "application/vnd.stardivision.writer"},
            {"vox", "application/x-authorware-bin"},
            {"vrml", "x-world/x-vrml"},
            {"vsd", "application/vnd.visio"},
            {"vsf", "application/vnd.vsf"},
            {"vss", "application/vnd.visio"},
            {"vst", "application/vnd.visio"},
            {"vsw", "application/vnd.visio"},
            {"vtu", "model/vnd.vtu"},
            {"vxml", "application/voicexml+xml"},
            {"w3d", "application/x-director"},
            {"wad", "application/x-doom"},
            {"wav", "audio/x-wav"},
            {"wax", "audio/x-ms-wax"},
            {"wbmp", "image/vnd.wap.wbmp"},
            {"wbs", "application/vnd.criticaltools.wbs+xml"},
            {"wbxml", "application/vnd.wap.wbxml"},
            {"wcm", "application/vnd.ms-works"},
            {"wdb", "application/vnd.ms-works"},
            {"wiz", "application/msword"},
            {"wks", "application/vnd.ms-works"},
            {"wma", "audio/x-ms-wma"},
            {"wmd", "application/x-ms-wmd"},
            {"wmf", "application/x-msmetafile"},
            {"wmlc", "application/vnd.wap.wmlc"},
            {"wmlsc", "application/vnd.wap.wmlscriptc"},
            {"wmls", "text/vnd.wap.wmlscript"},
            {"wml", "text/vnd.wap.wml"},
            {"wm", "video/x-ms-wm"},
            {"wmv", "video/x-ms-wmv"},
            {"wmx", "video/x-ms-wmx"},
            {"wmz", "application/x-ms-wmz"},
            {"wpd", "application/vnd.wordperfect"},
            {"wpl", "application/vnd.ms-wpl"},
            {"wps", "application/vnd.ms-works"},
            {"wqd", "application/vnd.wqd"},
            {"wri", "application/x-mswrite"},
            {"wrl", "x-world/x-vrml"},
            {"wrz", "x-world/x-vrml"},
            {"wsdl", "application/wsdl+xml"},
            {"wspolicy", "application/wspolicy+xml"},
            {"wtb", "application/vnd.webturbo"},
            {"wvx", "video/x-ms-wvx"},
            {"x32", "application/x-authorware-bin"},
            {"x3d", "application/vnd.hzn-3d-crossword"},
            {"xaf", "x-world/x-vrml"},
            {"xap", "application/x-silverlight-app"},
            {"xar", "application/vnd.xara"},
            {"xbap", "application/x-ms-xbap"},
            {"xbd", "application/vnd.fujixerox.docuworks.binder"},
            {"xbm", "image/x-xbitmap"},
            {"xdm", "application/vnd.syncml.dm+xml"},
            {"xdp", "application/vnd.adobe.xdp+xml"},
            {"xdw", "application/vnd.fujixerox.docuworks"},
            {"xenc", "application/xenc+xml"},
            {"xer", "application/patch-ops-error+xml"},
            {"xfdf", "application/vnd.adobe.xfdf"},
            {"xfdl", "application/vnd.xfdl"},
            {"xht", "application/xhtml+xml"},
            {"xhtml", "application/xhtml+xml"},
            {"xhvml", "application/xv+xml"},
            {"xif", "image/vnd.xiff"},
            {"xla", "application/vnd.ms-excel"},
            {"xlam", "application/vnd.ms-excel.addin.macroenabled.12"},
            {"xlb", "application/vnd.ms-excel"},
            {"xlc", "application/vnd.ms-excel"},
            {"xlm", "application/vnd.ms-excel"},
            {"xls", "application/vnd.ms-excel"},
            {"xlsb", "application/vnd.ms-excel.sheet.binary.macroenabled.12"},
            {"xlsm", "application/vnd.ms-excel.sheet.macroenabled.12"},
            {"xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},
            {"xlt", "application/vnd.ms-excel"},
            {"xltm", "application/vnd.ms-excel.template.macroenabled.12"},
            {"xltx", "application/vnd.openxmlformats-officedocument.spreadsheetml.template"},
            {"xlw", "application/vnd.ms-excel"},
            {"xml", "application/xml"},
            {"xo", "application/vnd.olpc-sugar"},
            {"xof", "x-world/x-vrml"},
            {"xop", "application/xop+xml"},
            {"xpdl", "application/xml"},
            {"xpi", "application/x-xpinstall"},
            {"xpm", "image/x-xpixmap"},
            {"xpr", "application/vnd.is-xpr"},
            {"xps", "application/vnd.ms-xpsdocument"},
            {"xpw", "application/vnd.intercon.formnet"},
            {"xpx", "application/vnd.intercon.formnet"},
            {"xsl", "application/xml"},
            {"xslt", "application/xslt+xml"},
            {"xsm", "application/vnd.syncml+xml"},
            {"xspf", "application/xspf+xml"},
            {"xul", "application/vnd.mozilla.xul+xml"},
            {"xvm", "application/xv+xml"},
            {"xvml", "application/xv+xml"},
            {"xwd", "image/x-xwindowdump"},
            {"xyz", "chemical/x-xyz"},
            {"z", "application/x-compress"},
            {"zaz", "application/vnd.zzazz.deck+xml"},
            {"zip", "application/zip"},
            {"zir", "application/vnd.zul"},
            {"zirz", "application/vnd.zul"},
            {"zmm", "application/vnd.handheld-entertainment+xml"}
    };

    public static String unfold(String s)
    {
        if (s == null) {
            return null;
        }
        return s.replaceAll("\r|\n", "");
    }

    private static String decode(String s, Message message)
    {
        if (s == null) {
            return null;
        }
        else {
            return DecoderUtil.decodeEncodedWords(s, message);
        }
    }

    public static String unfoldAndDecode(String s)
    {
        return unfoldAndDecode(s, null);
    }

    public static String unfoldAndDecode(String s, Message message)
    {
        return decode(unfold(s), message);
    }

    // TODO implement proper foldAndEncode
    public static String foldAndEncode(String s)
    {
        return s;
    }

    /**
     * Returns the named parameter of a header field.
     *
     * If name is {@code null} the "value" of the header is returned, i.e. "text/html" in the following example:
     * {@code Content-Type: text/html; charset="utf-8"}
     * Note: Parsing header parameters is not a very cheap operation. Prefer using {@code MimeParameterDecoder}
     * directly over calling this method multiple times for extracting different parameters from the the same header.
     *
     * @param headerBody The header body.
     * @param name The parameter name. Might be {@code null}.
     * @return the (parameter) value. if the parameter cannot be found the method returns null.
     */
    public static String getHeaderParameter(String headerBody, String name)
    {
        if (headerBody == null) {
            return null;
        }
        headerBody = headerBody.replaceAll("[\r\n]", "");
        String[] parts = headerBody.split(";");
        if (name == null && parts.length > 0) {
            return parts[0].trim();
        }

        if (name == null) {
            return MimeParameterDecoder.extractHeaderValue(headerBody);
        }
        else {
            MimeValue mimeValue = MimeParameterDecoder.decode(headerBody);
            return mimeValue.getParameters().get(name.toLowerCase(Locale.ROOT));
        }
    }

    public static Map<String, String> getAllHeaderParameters(String headerBody)
    {
        Map<String, String> result = new HashMap<>();

        headerBody = headerBody.replaceAll("[\r\n]", "");
        String[] parts = headerBody.split(";");
        for (String part : parts) {
            String[] partParts = part.split("=", 2);
            if (partParts.length == 2) {
                String parameterName = partParts[0].trim().toLowerCase(Locale.US);
                String parameterValue = partParts[1].trim();
                result.put(parameterName, parameterValue);
            }
        }
        return result;
    }


    public static Part findFirstPartByMimeType(Part part, String mimeType)
    {
        if (part.getBody() instanceof Multipart) {
            Multipart multipart = (Multipart) part.getBody();
            for (BodyPart bodyPart : multipart.getBodyParts()) {
                Part ret = MimeUtility.findFirstPartByMimeType(bodyPart, mimeType);
                if (ret != null) {
                    return ret;
                }
            }
        }
        else if (isSameMimeType(part.getMimeType(), mimeType)) {
            return part;
        }
        return null;
    }

    /**
     * Returns true if the given mimeType matches the matchAgainst specification.
     *
     * @param mimeType A MIME type to check.
     * @param matchAgainst A MIME type to check against. May include wildcards such as image/* or * /*.
     * @return
     */
    public static boolean mimeTypeMatches(String mimeType, String matchAgainst)
    {
        Pattern p = Pattern.compile(matchAgainst.replaceAll("\\*", "\\.\\*"), Pattern.CASE_INSENSITIVE);
        return p.matcher(mimeType).matches();
    }

    public static boolean isDefaultMimeType(String mimeType)
    {
        return isSameMimeType(mimeType, DEFAULT_ATTACHMENT_MIME_TYPE);
    }

    /**
     * Get decoded contents of a body.
     * <p/>
     * Right now only some classes retain the original encoding of the body contents. Those classes
     * have to implement the {@link RawDataBody} interface in order for this method to decode the
     * data delivered by {@link Body#getInputStream()}.
     * <p/>
     * The ultimate goal is to get to a point where all classes retain the original data and
     * {@code RawDataBody} can be merged into {@link Body}.
     */
    public static InputStream decodeBody(Body body)
            throws MessagingException
    {
        InputStream inputStream;
        if (body instanceof RawDataBody) {
            RawDataBody rawDataBody = (RawDataBody) body;
            String encoding = rawDataBody.getEncoding();
            final InputStream rawInputStream = rawDataBody.getInputStream();
            if (MimeUtil.ENC_7BIT.equalsIgnoreCase(encoding)
                    || MimeUtil.ENC_8BIT.equalsIgnoreCase(encoding)
                    || MimeUtil.ENC_BINARY.equalsIgnoreCase(encoding)) {
                inputStream = rawInputStream;
            }
            else if (MimeUtil.ENC_BASE64.equalsIgnoreCase(encoding)) {
                inputStream = new Base64InputStream(rawInputStream, false)
                {
                    @Override
                    public void close()
                            throws IOException
                    {
                        super.close();
                        closeInputStreamWithoutDeletingTemporaryFiles(rawInputStream);
                    }
                };
            }
            else if (MimeUtil.ENC_QUOTED_PRINTABLE.equalsIgnoreCase(encoding)) {
                inputStream = new QuotedPrintableInputStream(rawInputStream)
                {
                    @Override
                    public void close()
                            throws IOException
                    {
                        super.close();
                        closeInputStreamWithoutDeletingTemporaryFiles(rawInputStream);
                    }
                };
            }
            else {
                Timber.w("Unsupported encoding: %s", encoding);
                inputStream = rawInputStream;
            }
        }
        else {
            inputStream = body.getInputStream();
        }
        return inputStream;
    }

    public static void closeInputStreamWithoutDeletingTemporaryFiles(InputStream rawInputStream)
            throws IOException
    {
        if (rawInputStream instanceof BinaryTempFileBody.BinaryTempFileBodyInputStream) {
            ((BinaryTempFileBody.BinaryTempFileBodyInputStream) rawInputStream).closeWithoutDeleting();
        }
        else {
            rawInputStream.close();
        }
    }

    public static String getMimeTypeByExtension(String filename)
    {
        String returnedType = null;
        String extension = null;

        if (filename != null && filename.lastIndexOf('.') != -1) {
            extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.US);
            returnedType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        // If the MIME type set by the user's mailer is application/octet-stream, try to figure
        // out whether there's a sane file type extension.
        if (returnedType != null && !isSameMimeType(returnedType, DEFAULT_ATTACHMENT_MIME_TYPE)) {
            return returnedType;
        }
        else if (extension != null) {
            for (String[] contentTypeMapEntry : MIME_TYPE_BY_EXTENSION_MAP) {
                if (contentTypeMapEntry[0].equals(extension)) {
                    return contentTypeMapEntry[1];
                }
            }
        }
        return DEFAULT_ATTACHMENT_MIME_TYPE;
    }

    public static String getExtensionByMimeType(@NonNull String mimeType)
    {
        String lowerCaseMimeType = mimeType.toLowerCase(Locale.US);
        for (String[] contentTypeMapEntry : MIME_TYPE_BY_EXTENSION_MAP) {
            if (contentTypeMapEntry[1].equals(lowerCaseMimeType)) {
                return contentTypeMapEntry[0];
            }
        }
        return null;
    }

    /**
     * Get a default content-transfer-encoding for use with a given content-type when adding an
     * unencoded attachment. It's possible that 8bit encodings may later be converted to 7bit for
     * 7bit transport.
     * <ul>
     * <li>null: base64
     * <li>message/rfc822: 8bit
     * <li>message/*: 7bit
     * <li>multipart/signed: 7bit
     * <li>multipart/*: 8bit
     * <li>*&#47;*: base64
     * </ul>
     *
     * @param type A String representing a MIME content-type
     * @return A String representing a MIME content-transfer-encoding
     */
    public static String getEncodingforType(String type)
    {
        if (type == null) {
            return (MimeUtil.ENC_BASE64);
        }
        else if (MimeUtil.isMessage(type)) {
            return (MimeUtil.ENC_8BIT);
        }
        else if (isSameMimeType(type, "multipart/signed") || isMessage(type)) {
            return (MimeUtil.ENC_7BIT);
        }
        else if (isMultipart(type)) {
            return (MimeUtil.ENC_8BIT);
        }
        else {
            return (MimeUtil.ENC_BASE64);
        }
    }

    public static boolean isMultipart(String mimeType)
    {
        return ((mimeType != null) && mimeType.toLowerCase(Locale.US).startsWith("multipart/"));
    }

    public static boolean isMessage(String mimeType)
    {
        return isSameMimeType(mimeType, "message/rfc822");
    }

    public static boolean isMessageType(String mimeType)
    {
        return mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("message/");
    }

    public static boolean isSameMimeType(String mimeType, String otherMimeType)
    {
        return ((mimeType != null) && mimeType.equalsIgnoreCase(otherMimeType));
    }
}
