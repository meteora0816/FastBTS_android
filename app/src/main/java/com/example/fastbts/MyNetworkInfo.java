package com.example.fastbts;

import java.util.List;

public class MyNetworkInfo {
    public String apiLevel;
    public String connectionType;
    public List<CellInfo> cellInfo;
    public WifiInfo wifiInfo;

    public static class CellInfo {
        public String cellType;
        public CellIdentity cellIdentity;
        public CellSignalStrength cellSignalStrength;
        public CellInfo(String cellType, CellIdentity cellIdentity, CellSignalStrength cellSignalStrength) {
            this.cellType = cellType;
            this.cellIdentity = cellIdentity;
            this.cellSignalStrength = cellSignalStrength;
        }

        public static class CellIdentity {

        }

        public static class CellIdentityCdma extends CellIdentity {
            public String basestationId;
            public String latitude;
            public String longitude;
            public String networkId;
            public String systemId;

            public CellIdentityCdma(String basestationId, String latitude, String longitude, String networkId, String systemId) {
                this.basestationId = basestationId;
                this.latitude = latitude;
                this.longitude = longitude;
                this.networkId = networkId;
                this.systemId = systemId;
            }
        }

        public static class CellIdentityGsm extends CellIdentity {
            public String arfcn;
            public String bsic;
            public String cid;
            public String lac;
            public String mcc;
            public String mnc;
            public String mobileNetworkOperator;

            public CellIdentityGsm(String arfcn, String bsic, String cid, String lac, String mcc, String mnc, String mobileNetworkOperator) {
                this.arfcn = arfcn;
                this.bsic = bsic;
                this.cid = cid;
                this.lac = lac;
                this.mcc = mcc;
                this.mnc = mnc;
                this.mobileNetworkOperator = mobileNetworkOperator;
            }
        }

        public static class CellIdentityLte extends CellIdentity {
            public String[] bands;
            public String bandwidth;
            public String ci;
            public String earfcn;
            public String mcc;
            public String mnc;
            public String mobileNetworkOperator;
            public String pci;
            public String tac;

            public CellIdentityLte(String[] bands, String bandwidth, String ci, String earfcn, String mcc, String mnc, String mobileNetworkOperator, String pci, String tac) {
                this.bands = bands;
                this.bandwidth = bandwidth;
                this.ci = ci;
                this.earfcn = earfcn;
                this.mcc = mcc;
                this.mnc = mnc;
                this.mobileNetworkOperator = mobileNetworkOperator;
                this.pci = pci;
                this.tac = tac;
            }
        }

        public static class CellIdentityWcdma extends CellIdentity {
            public String cid;
            public String lac;
            public String mcc;
            public String mnc;
            public String mobileNetworkOperator;
            public String psc;
            public String uarfcn;

            public CellIdentityWcdma(String cid, String lac, String mcc, String mnc, String mobileNetworkOperator, String psc, String uarfcn) {
                this.cid = cid;
                this.lac = lac;
                this.mcc = mcc;
                this.mnc = mnc;
                this.mobileNetworkOperator = mobileNetworkOperator;
                this.psc = psc;
                this.uarfcn = uarfcn;
            }
        }

        public static class CellIdentityTdscdma extends CellIdentity {
            public String cid;
            public String cpid;
            public String lac;
            public String mcc;
            public String mnc;
            public String mobileNetworkOperator;
            public String uarfcn;

            public CellIdentityTdscdma(String cid, String cpid, String lac, String mcc, String mnc, String mobileNetworkOperator, String uarfcn) {
                this.cid = cid;
                this.cpid = cpid;
                this.lac = lac;
                this.mcc = mcc;
                this.mnc = mnc;
                this.mobileNetworkOperator = mobileNetworkOperator;
                this.uarfcn = uarfcn;
            }
        }

        public static class CellIdentityNr extends CellIdentity {
            public String[] bands;
            public String mcc;
            public String mnc;
            public String nci;
            public String nrarfcn;
            public String pci;
            public String tac;

            public CellIdentityNr(String[] bands, String mcc, String mnc, String nci, String nrarfcn, String pci, String tac) {
                this.bands = bands;
                this.mcc = mcc;
                this.mnc = mnc;
                this.nci = nci;
                this.nrarfcn = nrarfcn;
                this.pci = pci;
                this.tac = tac;
            }
        }


        public static class CellSignalStrength {

        }

        public static class CellSignalStrengthCdma extends CellSignalStrength {
            public String asuLevel;
            public String cdmaDbm;
            public String cdmaEcio;
            public String cdmaLevel;
            public String dbm;
            public String evdodbm;
            public String evdoEcio;
            public String evdoLevel;
            public String evdoSnr;
            public String level;

            public CellSignalStrengthCdma(String asuLevel, String cdmaDbm, String cdmaEcio, String cdmaLevel, String dbm, String evdodbm, String evdoEcio, String evdoLevel, String evdoSnr, String level) {
                this.asuLevel = asuLevel;
                this.cdmaDbm = cdmaDbm;
                this.cdmaEcio = cdmaEcio;
                this.cdmaLevel = cdmaLevel;
                this.dbm = dbm;
                this.evdodbm = evdodbm;
                this.evdoEcio = evdoEcio;
                this.evdoLevel = evdoLevel;
                this.evdoSnr = evdoSnr;
                this.level = level;
            }
        }

        public static class CellSignalStrengthGsm extends CellSignalStrength {
            public String asuLevel;
            public String bitErrorRate;
            public String dbm;
            public String level;
            public String rssi;
            public String timingAdvance;

            public CellSignalStrengthGsm(String asuLevel, String bitErrorRate, String dbm, String level, String rssi, String timingAdvance) {
                this.asuLevel = asuLevel;
                this.bitErrorRate = bitErrorRate;
                this.dbm = dbm;
                this.level = level;
                this.rssi = rssi;
                this.timingAdvance = timingAdvance;
            }
        }

        public static class CellSignalStrengthLte extends CellSignalStrength {
            public String asuLevel;
            public String cqi;
            public String cqiTableIndex;
            public String dbm;
            public String level;
            public String rsrp;
            public String rsrq;
            public String rssi;
            public String rssnr;
            public String timingAdvance;

            public CellSignalStrengthLte(String asuLevel, String cqi, String cqiTableIndex, String dbm, String level, String rsrp, String rsrq, String rssi, String rssnr, String timingAdvance) {
                this.asuLevel = asuLevel;
                this.cqi = cqi;
                this.cqiTableIndex = cqiTableIndex;
                this.dbm = dbm;
                this.level = level;
                this.rsrp = rsrp;
                this.rsrq = rsrq;
                this.rssi = rssi;
                this.rssnr = rssnr;
                this.timingAdvance = timingAdvance;
            }
        }

        public static class CellSignalStrengthWcdma extends CellSignalStrength {
            public String asuLevel;
            public String dbm;
            public String ecNo;
            public String level;

            public CellSignalStrengthWcdma(String asuLevel, String dbm, String ecNo, String level) {
                this.asuLevel = asuLevel;
                this.dbm = dbm;
                this.ecNo = ecNo;
                this.level = level;
            }
        }

        public static class CellSignalStrengthTdscdma extends CellSignalStrength {
            public String asuLevel;
            public String dbm;
            public String level;
            public String rscp;

            public CellSignalStrengthTdscdma(String asuLevel, String dbm, String level, String rscp) {
                this.asuLevel = asuLevel;
                this.dbm = dbm;
                this.level = level;
                this.rscp = rscp;
            }
        }

        public static class CellSignalStrengthNr extends CellSignalStrength {
            public String asuLevel;
            public List<String> csicqiReport;
            public String csicqiTableIndex;
            public String csiRsrp;
            public String csiRsrq;
            public String csiSinr;
            public String dbm;
            public String level;
            public String ssRsrp;
            public String ssRsrq;
            public String ssSinr;

            public CellSignalStrengthNr(String asuLevel, List<String> csicqiReport, String csicqiTableIndex, String csiRsrp, String csiRsrq, String csiSinr, String dbm, String level, String ssRsrp, String ssRsrq, String ssSinr) {
                this.asuLevel = asuLevel;
                this.csicqiReport = csicqiReport;
                this.csicqiTableIndex = csicqiTableIndex;
                this.csiRsrp = csiRsrp;
                this.csiRsrq = csiRsrq;
                this.csiSinr = csiSinr;
                this.dbm = dbm;
                this.level = level;
                this.ssRsrp = ssRsrp;
                this.ssRsrq = ssRsrq;
                this.ssSinr = ssSinr;
            }
        }
    }

    public static class WifiInfo {
        /* Added in API 1 */
        public String SSID;
        public String BSSID;
        public String rssi;
        public String linkSpeed;
        public String networkId;

        /* Added in API 21 */
        public String frequency;

        /* Added in API 29 */
        public String passpointFqdn;
        public String passpointProviderFriendlyName;
        public String rxLinkSpeedMbps;
        public String txLinkSpeedMbps;

        /* Added in API 30 */
        public String maxSupportedRxLinkSpeedMbps;
        public String maxSupportedTxLinkSpeedMbps;
        public String wifiStandard;

        /* Added in API 31 */
        public String currentSecurityType;
        public String subscriptionId;

        public WifiInfo(String SSID, String BSSID, String rssi, String linkSpeed, String networkId, String frequency,
                        String passpointFqdn, String passpointProviderFriendlyName, String rxLinkSpeedMbps, String txLinkSpeedMbps,
                        String maxSupportedRxLinkSpeedMbps, String maxSupportedTxLinkSpeedMbps, String wifiStandard,
                        String currentSecurityType, String subscriptionId) {
            this.SSID = SSID;
            this.BSSID = BSSID;
            this.rssi = rssi;
            this.linkSpeed = linkSpeed;
            this.networkId = networkId;
            this.frequency = frequency;
            this.passpointFqdn = passpointFqdn;
            this.passpointProviderFriendlyName = passpointProviderFriendlyName;
            this.rxLinkSpeedMbps = rxLinkSpeedMbps;
            this.txLinkSpeedMbps = txLinkSpeedMbps;
            this.maxSupportedRxLinkSpeedMbps = maxSupportedRxLinkSpeedMbps;
            this.maxSupportedTxLinkSpeedMbps = maxSupportedTxLinkSpeedMbps;
            this.wifiStandard = wifiStandard;
            this.currentSecurityType = currentSecurityType;
            this.subscriptionId = subscriptionId;
        }
    }

    public MyNetworkInfo(String apiLevel, String connectionType, List<CellInfo> cellInfo, WifiInfo wifiInfo) {
        this.apiLevel = apiLevel;
        this.connectionType = connectionType;
        this.cellInfo = cellInfo;
        this.wifiInfo = wifiInfo;
    }
}
