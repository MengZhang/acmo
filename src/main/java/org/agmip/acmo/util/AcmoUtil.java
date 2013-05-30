package org.agmip.acmo.util;

import au.com.bytecode.opencsv.CSVReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.agmip.util.MapUtil;
import org.agmip.ace.LookupCodes;
import org.agmip.ace.util.AcePathfinderUtil;
import org.agmip.dome.DomeUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AcmoUtil {
    private static final Logger log = LoggerFactory.getLogger(AcmoUtil.class);
    /**
     * Do not instantiate
     */
    private AcmoUtil(){}


    /**
     * Write an ACMO file to the specified {@code outputPath} which includes
     * all the experiments in the {@code datapackage}.
     *
     * @param outputPath the directory to write the ACMO_meta.dat file.
     * @param datapackage a standard AgMIP package
     *
     */
    public static void writeAcmo(String outputPath, HashMap<String, Object> datapackage, String destModel) {
        HashMap<String, String> wstClimMap = new HashMap<String, String>();
        try {
            // Make sure the outputPath exists
            File f = new File(outputPath);
            f.mkdirs();
            String fileName   = outputPath+File.separator+"ACMO_meta.dat";
            log.debug("Attempting to write {}", fileName);
            FileWriter fw     = new FileWriter(fileName);
            BufferedWriter bw = new BufferedWriter(fw);
            // Index the Weather Stations
            for (HashMap<String, Object> wst : MapUtil.getRawPackageContents(datapackage, "weathers")) {
                wstClimMap.put(MapUtil.getValueOr(wst, "wst_id", ""), MapUtil.getValueOr(wst, "clim_id", "0XXX"));
            }

            try {
                // First write the header
                bw.write(generateAcmoHeader());
                 // Then write the lines
                ArrayList<HashMap<String, Object>> experiments = MapUtil.getRawPackageContents(datapackage, "experiments");
                for (HashMap<String, Object> experiment : experiments) {
                    // get WSTID and pass the CLIM_ID from that.
                    String wstId = MapUtil.getValueOr(experiment, "wst_id", "");
                    String climId = wstClimMap.get(wstId);
                    if (climId == null) {
                        climId = "0XXX";
                    }
                    String acmoData = extractAcmoData(experiment, destModel, climId);
                    log.debug("ACMO dataline: {}", acmoData);
                    bw.write(acmoData);
                    bw.write("\n");
                }
            } finally {
                // Finally close the file.
                log.debug("Attempting to close the ACMO file");
                bw.flush();
                bw.close();
                fw.close();
            }
        } catch (Exception ex) {
            log.error("Error writing ACMO_meta.dat: {}", ex.getMessage());
        }
    }

    /**
     * Extract a CSV-compatable ACMO line from a single experiment
     *
     * @param dataset a single AgMIP dataset
     *
     * @return ACMO compatible CSV line.
     */
    public static String extractAcmoData(HashMap<String, Object> dataset, String destModel, String climId) {
        ArrayList<String> acmoData = new ArrayList<String>();
        HashMap<String, Object> observed = MapUtil.getRawBucket(dataset, "observed");
        HashMap<String, String> events   = extractEventData(dataset, destModel);


        /**
         * Data to get:
         * wst_id (root)
         * soil_id (root)
         * fl_lat (root)
         * fl_lon (root)
         * crid (events)
         * cul_id (events)
         * cul_name (events)
         * sdat (root)
         * pdate (events)
         * hdate (observed)
         * hwah (observed)
         * cwah (observed)
         * ir_count (events)
         * ir_tot (events)
         * irop (events)
         * fe_count (events)
         * fen_tot (events)
         * fep_tot (events)
         * fek_tot (events)
         * omamt (events)
         * ti_count (events)
         * tiimp (events)
         * crop model (passed into writeAcmo())
         */

        String fieldOverlayString = MapUtil.getValueOr(dataset, "field_overlay", "");
        String seasonalStrategyString = MapUtil.getValueOr(dataset, "seasonal_strategy", "");
        HashMap<String, String> domeBase = new HashMap<String, String>();

        acmoData.add("*"); // Just an indication mark, this line stands alone
        acmoData.add(quoteMe(MapUtil.getValueOr(dataset, "exname", ""))); // Will be generated by the database
        acmoData.add("1");
        acmoData.add(quoteMe(MapUtil.getValueOr(dataset, "field_overlay", "")));
        acmoData.add(quoteMe(MapUtil.getValueOr(dataset, "seasonal_strategy", "")));
        acmoData.add(quoteMe(MapUtil.getValueOr(dataset, "exname", "")));
        acmoData.add(quoteMe(MapUtil.getValueOr(dataset, "trt_name", "")));
        acmoData.add(quoteMe(climId));
        acmoData.add("1");
        if (! seasonalStrategyString.equals("")) {
            domeBase = DomeUtil.unpackDomeName(seasonalStrategyString);
        } else if (! fieldOverlayString.equals("")){
            domeBase = DomeUtil.unpackDomeName(fieldOverlayString);
        }
        acmoData.add(quoteMe(MapUtil.getValueOr(domeBase, "reg_id", ""))); // Region
        acmoData.add(quoteMe(MapUtil.getValueOr(domeBase, "stratum", ""))); // Stratum
        acmoData.add(MapUtil.getValueOr(domeBase, "rap_id", "")); // RAP ID
        acmoData.add(MapUtil.getValueOr(domeBase, "man_id", "")); // MAN ID
        acmoData.add("AgMIP"); // Institution
        acmoData.add(MapUtil.getValueOr(dataset, "rotation", "0"));
        acmoData.add(MapUtil.getValueOr(dataset, "wst_id", ""));
        acmoData.add(MapUtil.getValueOr(dataset, "soil_id", ""));
        acmoData.add(MapUtil.getValueOr(dataset, "fl_lat", ""));
        acmoData.add(MapUtil.getValueOr(dataset, "fl_long", ""));
        acmoData.add(quoteMe(MapUtil.getValueOr(events, "crid", "")));
        acmoData.add(MapUtil.getValueOr(events, "cul_id", ""));
        acmoData.add(quoteMe(MapUtil.getValueOr(events, "cul_name", "")));
        acmoData.add(correctDateFormat(MapUtil.getValueOr(dataset, "sdat", "")));
        acmoData.add(correctDateFormat(MapUtil.getValueOr(events, "pdate", "")));
        acmoData.add(MapUtil.getValueOr(observed, "hwah", ""));
        acmoData.add(MapUtil.getValueOr(observed, "cwah", ""));
        acmoData.add(correctDateFormat(MapUtil.getValueOr(observed, "hdate", "")));
        acmoData.add(MapUtil.getValueOr(events, "ir_count", ""));
        acmoData.add(MapUtil.getValueOr(events, "ir_tot", ""));
        acmoData.add(quoteMe(MapUtil.getValueOr(events, "irop", "")));
        acmoData.add(MapUtil.getValueOr(events, "fe_count", ""));
        acmoData.add(MapUtil.getValueOr(events, "fen_tot", ""));
        acmoData.add(MapUtil.getValueOr(events, "fep_tot", ""));
        acmoData.add(MapUtil.getValueOr(events, "fek_tot", ""));
        acmoData.add(MapUtil.getValueOr(events, "omamt", ""));
        acmoData.add(MapUtil.getValueOr(events, "ti_count", ""));
        acmoData.add(quoteMe(MapUtil.getValueOr(events, "tiimp", "")));
        acmoData.add(destModel.toUpperCase());
        return joinList(acmoData, ",");
    }

    /**
     * Generates the standard ACMO header (with all the variables defined)
     *
     * @return ACMO header
     */
    public static String generateAcmoHeader() {
        return "!,Experiment ID,,Field Overlay ID,Seasonal Strategy ID,\"Name of experiment, field test or survey\",Treatment Name,4-character Climate ID code,Climate replication number for multiple realizations of weather data (ask Alex),Region ID,Regional stratum identification number,RAP ID,\"Management regimen ID, for multiple management regimens per RAP\",Names of institutions involved in collection of field or survey data,\"Crop rotation indicator (=1 to indicate that this is a continuous, multi-year simulation, =0 for single year simulations)\",Weather station ID,Soil ID,Site Latitude,Site Longitude,Crop type (common name) ,Crop model-specific cultivar ID,Cultivar name,Start of simulation date,Planting date,\"Observed harvested yield, dry weight\",Observed total above-ground biomass at harvest,Observed harvest date,Total number of irrigation events,Total amount of irrigation,Type of irrigation application,Total number of fertilizer applications,Total N applied,Total P applied,Total K applied,Manure and applied oganic matter,Total number of tillage applications,\"Tillage type (hand, animal or mechanized)\",\"Short name of crop model used for simulations (e.g., DSSAT, APSIM, Aquacrop, STICS, etc.)\",Model name and version number of the crop model used to generate simulated outputs,\"Simulated harvest yield, dry matter\",\"Simulated above-ground biomass at harvest, dry matter\",Simulated anthesis date,Simulated maturity date,Simulated harvest date,\"Simulated leaf area index, maximum\",Total precipitation from planting to harvest,\"Simulated evapotranspiration, planting to harvest\",Simulated N uptake during season,Simulated N leached up to harvest maturity,\n!,text,number,text,text,text,text,code,number,code,number,code,code,text,number,text,text,decimal degrees,decimal degrees,text,text,text,yyyy-mm-dd,yyyy-mm-dd,kg/ha,kg/ha,yyyy-mm-dd,number,mm,text,number,kg[N]/ha,kg[P]/ha,kg[K]/ha,kg/ha,#,text,text,text,kg/ha,kg/ha,yyyy-mm-dd,yyyy-mm-dd,yyyy-mm-dd,m2/m2,mm,mm,kg/ha,kg/ha,\n#,EID,RUN#,FIELD_OVERLAY,SEASONAL_STRATEGY,EXNAME,TRT_NAME,CLIM_ID,CLIM_REP,REG_ID,STRATUM,RAP_ID,MAN_ID,INSTITUTION,ROTATION,WST_ID,SOIL_ID,FL_LAT,FL_LONG,CRID_text,CUL_ID,CUL_NAME,SDAT,PDATE,HWAH,CWAH,HDATE,IR#C,IR_TOT,IROP_text,FE_#,FEN_TOT,FEP_TOT,FEK_TOT,OM_TOT,TI_#,TIIMP_text,CROP_MODEL,MODEL_VER,HWAH_S,CWAH_S,ADAT_S,MDAT_S,HADAT_S,LAIX_S,PRCP_S,ETCP_S,NUCM_S,NLCM_S,\n";
    }

    protected static HashMap<String, String> extractEventData(HashMap<String, Object> dataset, String destModel) {
        destModel = destModel.toLowerCase();
        HashMap<String, String> results = new HashMap<String, String>();
        HashMap<String, Object> management = MapUtil.getRawBucket(dataset, "management");
        ArrayList<HashMap<String, String>> events = (ArrayList<HashMap<String, String>>) MapUtil.getObjectOr(management, "events", new ArrayList<HashMap<String, String>>());
        int irrCount = 0;
        int feCount = 0;
        int tilCount = 0;
        ArrayList<String> irop = new ArrayList<String>();
        ArrayList<String> timp = new ArrayList<String>();
        BigDecimal irrAmount, fenAmount, fekAmount, fepAmount, omAmount = null;
        try {
            irrAmount = new BigDecimal(0.0);
            fenAmount = new BigDecimal(0.0);
            fekAmount = new BigDecimal(0.0);
            fepAmount = new BigDecimal(0.0);
            omAmount  = new BigDecimal(0.0);
        } catch (Exception ex) {
            //Something really weird happened here. Like really really weird.
            log.error("Error converting 0.0 to a decimal, hard Java error");
            return new HashMap<String, String>();
        }
        // Process only the items needed by ACMO.

        for (HashMap<String, String> event : events) {
            // From planting, we need to extract PDATE, CUL_ID, CUL_NAME,
            // and CRID (as full text)

            String currentEvent = MapUtil.getValueOr(event, "event", "");
            log.debug("Current event: {}", event.toString());
            if (currentEvent.equals("planting")) {
                populateEventMap(results, event, "pdate", destModel);
                populateEventMap(results, event, "cul_name", destModel);

                if (event.containsKey(destModel+"_cul_id")) {
                    populateEventMap(results, event, destModel+"_cul_id", destModel);
                } else {
                    populateEventMap(results, event, "cul_id", destModel);
                }

                String crop = LookupCodes.lookupCode("crid", MapUtil.getValueOr(event, "crid", ""), "cn");
                results.put("crid", crop);
            } else if (currentEvent.equals("irrigation")) {
                irrCount++;
                String irval = MapUtil.getValueOr(event, "irval", "");
                String sIrOp = LookupCodes.lookupCode("irop", MapUtil.getValueOr(event, "irop", ""), "cn");
                try {
                    if (!irval.equals("")) {
                        irrAmount = irrAmount.add(new BigDecimal(irval));
                    }
                } catch (Exception ex) {
                    log.error("Error converting irrigation amount with value {}", irval);
                    continue;
                }
                if (! irop.contains(sIrOp)) {
                    irop.add(sIrOp);
                }
            } else if (currentEvent.equals("fertilizer")) {
                feCount++;
                String feamn = MapUtil.getValueOr(event, "feamn", "");
                String feamk = MapUtil.getValueOr(event, "feamk", "");
                String feamp = MapUtil.getValueOr(event, "feamp", "");
                log.debug("Feamn amount: {}", feamn);
                 try {
                     if (!feamn.equals("")) {
                        fenAmount = fenAmount.add(new BigDecimal(feamn));
                    }
                } catch (Exception ex) {
                    log.error("Error converting fertilizer [nitrogen] with value {}", feamn);
                    continue;
                }
                log.debug(fenAmount.toString());

                try {
                    if (!feamk.equals("")) {
                        fekAmount = fekAmount.add(new BigDecimal(feamk));
                    }
                } catch (Exception ex) {
                    log.error("Error converting fertilizer [potassium] with value {}", feamn);
                    continue;
                }

                try {
                    if (!feamp.equals("")) {
                        fepAmount = fepAmount.add(new BigDecimal(feamp));
                    }
                } catch (Exception ex) {
                    log.error("Error converting fertilizer [phosphorus] with value {}", feamn);
                    continue;
                }
            } else if (currentEvent.equals("organic_matter")) {
                String omamt = MapUtil.getValueOr(event, "omamt", "");
                if (! omamt.equals("")) {
                    try {
                        omAmount = omAmount.add(new BigDecimal(omamt));
                    } catch (Exception ex) {
                        log.error("Error converting organic matter amount with value {}", omamt);
                        continue;
                    }
                }
            } else if (currentEvent.equals("tillage")) {
                tilCount++;
                String tiimp = LookupCodes.lookupCode("tiimp", MapUtil.getValueOr(event, "tiimp", ""), "ca");
                if (! timp.contains(tiimp)) {
                    timp.add(tiimp);
                }
            }
        }
        // After processing all the events, consume the results of the counters.
        if (irrCount > 0) {
            results.put("ir_count", Integer.toString(irrCount));
            results.put("ir_tot", irrAmount.toString());
            results.put("irop", joinList(irop, "|"));
        }
        if (feCount > 0) {
            results.put("fe_count", Integer.toString(feCount));
            results.put("fen_tot", fenAmount.toString());
            results.put("fek_tot", fekAmount.toString());
            results.put("fep_tot", fepAmount.toString());
        }
        String om_tot = omAmount.toString();
        if (! om_tot.equals("0")) {
            results.put("omamt", om_tot);
        }
        if (tilCount > 0) {
            results.put("ti_count", Integer.toString(tilCount));
            results.put("tiimp", joinList(timp, "|"));
        }
        log.debug("extractEventData results: {}", results.toString());
        return results;
    }

    private static void populateEventMap(HashMap<String, String> eventMap, HashMap<String, String> sourceMap, String var, String destModel) {
        String tempVar = AcePathfinderUtil.setEventDateVar(var, true);
        String value = MapUtil.getValueOr(sourceMap, tempVar, "");
        if (var.startsWith(destModel)) {
            var = var.substring(destModel.length()+1);
        }
        eventMap.put(var, value);
    }

    private static String joinList(ArrayList<String> list, String joint) {
        StringBuilder joinedList = new StringBuilder();
        for (String item : list) {
            joinedList.append(item);
            joinedList.append(joint);
        }
        if (joinedList.length() > 0)
            joinedList.setLength(joinedList.length()-1);
        return joinedList.toString();
    }

    private static String correctDateFormat(String date) {
        if (! date.equals("")) {
            StringBuilder d = new StringBuilder(date);
            d.insert(4, "-");
            d.insert(7, "-");
            return d.toString();
        } else {
            return date;
        }
    }

    private static String quoteMe(String unquoted) {
        return "\""+unquoted+"\"";
    }
    
    /**
     * Generate an ACMO CSV file object with a non-repeated file name in the
     * given directory. The naming rule is as follow,
     * ACMO-[Region]-[stratum]-[climate_id]-[RAP_id]-[Management_id]-[model].csv
     *
     * @param outputCsvPath The output path for CSV file
     * @param mode The name of model which provide the model output data
     * @return The {@code File} for CSV file
     */
    public static File createCsvFile(String outputCsvPath, String mode) {
        return createCsvFile(outputCsvPath, mode, null);
    }

    /**
     * Generate an ACMO CSV file object with a non-repeated file name in the
     * given directory.
     *
     * @param outputCsvPath The output path for CSV file
     * @param mode The name of model which provide the model output data
     * @return The {@code File} for CSV file
     */
    public static File createCsvFile(String outputCsvPath, String mode, String metaFilePath) {
        if (!outputCsvPath.endsWith(File.separator) && !outputCsvPath.equals("")) {
            outputCsvPath += File.separator;
        }
        String domeInfo = "";
        if (metaFilePath != null) {
            try {
                // Read meta data
                CSVReader reader = new CSVReader(new FileReader(metaFilePath), ',', '"');
                List<String[]> metaData = reader.readAll();
                // Get Title and first record
                String[] title = new String[0];
                String[] data = new String[0];
                for (int i = 0; i < metaData.size() - 1; i++) {
                    if ("#".equals(metaData.get(i)[0])) {
                        title = metaData.get(i);
                        data = metaData.get(i + 1);
                        break;
                    }
                }
                // Get the position index of Region, stratum, climate ID, RAP ID and Management ID
                int region = -1;
                int stratum = -1;
                int climateId = -1;
                int rapId = -1;
                int mgnId = -1;
                int field = -1;
                int seasonal = -1;
                int count = 0;
                for (int i = 0; i < title.length; i++) {
                    if ("REG_ID".equalsIgnoreCase(title[i])) {
                        region = i;
                        count++;
                    } else if ("STRATUM".equalsIgnoreCase(title[i])) {
                        stratum = i;
                        count++;
                    } else if ("CLIM_ID".equalsIgnoreCase(title[i])) {
                        climateId = i;
                        count++;
                    } else if ("RAP_ID".equalsIgnoreCase(title[i])) {
                        rapId = i;
                        count++;
                    } else if ("MAN_ID".equalsIgnoreCase(title[i])) {
                        mgnId = i;
                        count++;
                    } else if ("FIELD_OVERLAY".equalsIgnoreCase(title[i])) {
                        field = i;
                        count++;
                    } else if ("SEASONAL_STRATEGY".equalsIgnoreCase(title[i])) {
                        seasonal = i;
                        count++;
                    } else {
                        continue;
                    }
                    if (count == 7) {
                        break;
                    }
                }
                // Get dome info for creating ACMO file name
                if (region != -1 && (stratum != -1 || rapId != -1 || mgnId != -1 || climateId != -1)) {
                    String str;
                    if ((str = getDomeInfoStr(data, region)).equals("0-")) {
                        if (!(str = getDomeInfoStr(data, seasonal)).equals("0-")) {
                        } else if (!(str = getDomeInfoStr(data, field)).equals("0-")) {
                        } else {
                            str = "";
                        }
                        if (!"".equals(str)) {
                            HashMap<String, String> domeBase = DomeUtil.unpackDomeName(str);
                            str = MapUtil.getValueOr(domeBase, "reg_id", "") + "-";
                        }
                    }
                    if (!str.equals("")) {
                        domeInfo = str;
                        domeInfo += getDomeInfoStr(data, stratum);
                        domeInfo += getDomeInfoStr(data, climateId);
                        domeInfo += getDomeInfoStr(data, rapId);
                        domeInfo += getDomeInfoStr(data, mgnId);
                    }
                }
            } catch (IOException ex) {
                domeInfo = "";
            }
        }
        // Create CSV file name
        outputCsvPath += "ACMO-" + domeInfo + mode;
        File f = new File(outputCsvPath + ".csv");
        int count = 1;
        while (f.exists()) {
            f = new File(outputCsvPath + " (" + count + ").csv");
            count++;
        }

        return f;
    }
    
    private static String getDomeInfoStr(String[] data, int id) {
        if (id < 0) {
            return "0-";
        } else if (id < data.length) {
            if ("".equals(data[id])) {
                return "0-";
            } else {
                return data[id] + "-";
            }
        } else {
            return "0-";
        }
    }

    /**
     * CSV Escape handling for given string.
     *  " -> ""
     *  , -> ","
     *
     * @param str The string will be escaped for CSV format output
     * @return Escaped CSV string
     */
    public static String escapeCsvStr(String str) {
        if (str != null || str.equals("")) {
            boolean needQuote = false;
            if (str.contains("\"")) {
                str = str.replaceAll("\"", "\"\"");
                needQuote = true;
            }
            if (!needQuote && str.contains(",")) {
                needQuote = true;
            }
            if (needQuote) {
                str = "\"" + str + "\"";
            }
            return str;
        } else {
            return "";
        }
    }
}
