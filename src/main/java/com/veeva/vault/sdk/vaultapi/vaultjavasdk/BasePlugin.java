package com.veeva.vault.sdk.vaultapi.vaultjavasdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.veeva.vault.sdk.vaultapi.vaultjavasdk.model.PluginSettings;
import com.veeva.vault.vapil.api.client.VaultClient;
import org.apache.log4j.Logger;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

public class BasePlugin extends AbstractMojo {

    protected VaultClient vaultClient;

    @Parameter(property = "vapilSettingsFilePath", defaultValue = "")
    protected String vapilSettingsFilePath = "";
    @Parameter(property = "pluginSettingsFilePath", defaultValue = "")
    protected String pluginSettingsFilePath;
    protected PluginSettings pluginSettings;
    protected String PACKAGE_NAME = null;
    protected Path PACKAGE_PATH = null;
    protected String IMPORT_LOG_FILE = LOG_OUTPUT_DESTINATION + "import-log";
    protected String DEPLOYMENT_LOG_FILE = LOG_OUTPUT_DESTINATION + "deployment-log";
    protected static final String USER_DIR = System.getProperty("user.dir");
    protected static final String DEPLOYMENT_DIRECTORY = USER_DIR + "/deployment/";
    protected static final String VPK_OUTPUT_DESTINATION = USER_DIR + "/deployment/packages/";
    protected static final String LOG_OUTPUT_DESTINATION = USER_DIR + "/deployment/logs/";
    protected HashMap<String, VaultClient.Builder> vaultClients = new HashMap<>();
    private static final Logger logger = Logger.getLogger(BasePlugin.class);

    private void initClients(String settingsJson) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            VaultClient.Settings settings = mapper.readValue(settingsJson, VaultClient.Settings.class);
            String vaultDNS = settings.getVaultDNS();
            if (vaultDNS.contains(",")) {
                for (String vaultDns : vaultDNS.split(",")) {
                    logger.info("Creating Vault Builder for dns : " + vaultDns.trim());
                    settings.setVaultDNS(vaultDns.trim());
                    String jsonString = settings.toJsonString();
                    VaultClient.Settings settings1 = mapper.readValue(jsonString, VaultClient.Settings.class);
                    logger.info(settings1);
                    vaultClients.put(vaultDns.trim(), VaultClient.newClientBuilderFromSettings(settings1));
                }
            }
        } catch (JsonProcessingException e) {
            logger.error("initAllClients: Failed to load Client Vault Settings; " + e.getMessage());
            throw new IllegalArgumentException("Invalid Vault Client Settings");
        }
    }

    protected void initAllClients() {
        if (vaultClients.isEmpty()) {
            if (vapilSettingsFilePath != null) {
                try {
                    String vapilSettings = convertFileToString(vapilSettingsFilePath);
                    initClients(vapilSettings);
                } catch (Exception e) {
                    logger.error("initAllClients : Failed to map Plugin Settings: " + e.getMessage());
                }
            } else {
                logger.error("initAllClients : File path for Vapil settings is not present.");
            }
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (pluginSettingsFilePath != null) {
            try {

                this.pluginSettings = mapSettingValues(pluginSettingsFilePath);

            } catch (Exception e) {
                logger.error("Failed to map Plugin Settings: " + e.getMessage());
                throw new IllegalArgumentException("Invalid Plugin Settings");
            }

            if (pluginSettings != null) {
                this.PACKAGE_NAME = pluginSettings.getPackageName() + ".vpk";
                this.PACKAGE_PATH = Paths.get(VPK_OUTPUT_DESTINATION, PACKAGE_NAME);
            }
        }
    }

    private static PluginSettings mapSettingValues(String pluginSettingsFilePath) throws Exception {

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            return mapper.readValue(convertFileToString(pluginSettingsFilePath), PluginSettings.class);

        } catch (IOException | IllegalArgumentException e) {
            logger.error("Failed to load Plugin Settings: " + e.getMessage());
            throw new Exception(e);
        }

    }

    private static String convertFileToString(String filePath) throws IOException {
        File file = new File(filePath);
        return new String(Files.readAllBytes(Paths.get(file.getPath())));
    }
}