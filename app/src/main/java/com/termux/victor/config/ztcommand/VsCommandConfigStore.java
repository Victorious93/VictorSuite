package com.termux.victor.config.ztcommand;


import android.util.SparseArray;

import com.termux.victor.config.ztcommand.config.AVncConfig;
import com.termux.victor.config.ztcommand.config.ConfirmedDialogConfig;
import com.termux.victor.config.ztcommand.config.ForWardOpenLeftConfig;
import com.termux.victor.config.ztcommand.config.ForWardOpenRightConfig;
import com.termux.victor.config.ztcommand.config.HelpConfig;
import com.termux.victor.config.ztcommand.config.KnowConfig;
import com.termux.victor.config.ztcommand.config.LnConfig;
import com.termux.victor.config.ztcommand.config.QRCodeEncoderConfig;
import com.termux.victor.config.ztcommand.config.RebootConfig;
import com.termux.victor.config.ztcommand.config.ToastConfig;
import com.termux.victor.config.ztcommand.config.VersionConfig;
import com.termux.victor.config.ztcommand.config.X11CommandHideConfig;
import com.termux.victor.config.ztcommand.config.X11CommandShowConfig;
import com.termux.victor.config.ztcommand.config.X11KeyBoardHideConfig;
import com.termux.victor.config.ztcommand.config.X11KeyBoardShowConfig;
import com.termux.victor.config.ztcommand.config.X11StatusConfig;
import com.termux.victor.config.ztcommand.config.XmlMenuConfig;
import com.termux.victor.config.ztcommand.config.VsBackgroundImageConfig;
import com.termux.victor.config.ztcommand.config.VsConfig;
import com.termux.victor.config.ztcommand.config.VsKeyConstants;

import java.util.HashMap;
import java.util.Map;

public class VsCommandConfigStore {
    private static final SparseArray<VsConfig> sparse_array_config = new SparseArray<>();
    private static final Map<String, Integer> map_array_command = new HashMap<>();
    static {
        // 需要在此添加命令，否则找不到config
        map_array_command.put(VsKeyConstants.ZT_COMMAND_TOAST, VsKeyConstants.ZT_ID_TOAST);
        map_array_command.put(VsKeyConstants.ZT_COMMAND_HELP, VsKeyConstants.ZT_ID_HELP);
        map_array_command.put(VsKeyConstants.ZT_COMMAND_KNOW, VsKeyConstants.ZT_ID_KNOW);

        map_array_command.put(VsKeyConstants.ZT_COMMAND_VERSION, VsKeyConstants.ZT_ID_VERSION);
        map_array_command.put(VsKeyConstants.ZT_COMMAND_VERSION_1, VsKeyConstants.ZT_ID_VERSION);

        map_array_command.put(VsKeyConstants.ZT_COMMAND_LEFT, VsKeyConstants.ZT_ID_LEFT);
        map_array_command.put(VsKeyConstants.ZT_COMMAND_LEFT_1, VsKeyConstants.ZT_ID_LEFT);

        map_array_command.put(VsKeyConstants.ZT_COMMAND_RIGHT, VsKeyConstants.ZT_ID_RIGHT);
        map_array_command.put(VsKeyConstants.ZT_COMMAND_RIGHT_1, VsKeyConstants.ZT_ID_RIGHT);

        map_array_command.put(VsKeyConstants.ZT_COMMAND_REBOOT, VsKeyConstants.ZT_ID_REBOOT);
        map_array_command.put(VsKeyConstants.ZT_COMMAND_REBOOT_1, VsKeyConstants.ZT_ID_REBOOT);

        map_array_command.put(VsKeyConstants.ZT_COMMAND_LN, VsKeyConstants.ZT_ID_LN);
        map_array_command.put(VsKeyConstants.ZT_COMMAND_VNC, VsKeyConstants.ZT_ID_VNC);

        map_array_command.put(VsKeyConstants.ZT_COMMAND_X11_COMMAND_SHOW, VsKeyConstants.ZT_ID_X11_COMMAND_SHOW);
        map_array_command.put(VsKeyConstants.ZT_COMMAND_X11_COMMAND_SHOW_1, VsKeyConstants.ZT_ID_X11_COMMAND_SHOW);

        map_array_command.put(VsKeyConstants.ZT_COMMAND_X11_COMMAND_HIDE, VsKeyConstants.ZT_ID_X11_COMMAND_SHOW);
        map_array_command.put(VsKeyConstants.ZT_COMMAND_X11_COMMAND_HIDE_1, VsKeyConstants.ZT_ID_X11_COMMAND_SHOW);

        map_array_command.put(VsKeyConstants.ZT_COMMAND_X11_STATUS, VsKeyConstants.ZT_ID_X11_STATUS);

        map_array_command.put(VsKeyConstants.ZT_COMMAND_X11_KEYBOARD_SHOW, VsKeyConstants.ZT_ID_X11_KEYBOARD_SHOW);
        map_array_command.put(VsKeyConstants.ZT_COMMAND_X11_KEYBOARD_SHOW_1, VsKeyConstants.ZT_ID_X11_KEYBOARD_SHOW);

        map_array_command.put(VsKeyConstants.ZT_COMMAND_X11_KEYBOARD_HIDE, VsKeyConstants.ZT_ID_X11_KEYBOARD_HIDE);
        map_array_command.put(VsKeyConstants.ZT_COMMAND_X11_KEYBOARD_HIDE_1, VsKeyConstants.ZT_ID_X11_KEYBOARD_HIDE);

        map_array_command.put(VsKeyConstants.ZT_COMMAND_CONFIRMED_DIALOG, VsKeyConstants.ZT_ID_CONFIRMED_DIALOG);
        map_array_command.put(VsKeyConstants.ZT_COMMAND_QR_CODE_ENCODER_CONFIG, VsKeyConstants.ZT_ID_QR_CODE_ENCODER_CONFIG);

        map_array_command.put(VsKeyConstants.ZT_COMMAND_BACKGROUND_IMAGE, VsKeyConstants.ZT_ID_BACKGROUND_IMAGE);
        map_array_command.put(VsKeyConstants.ZT_COMMAND_BACKGROUND_IMAGE_1, VsKeyConstants.ZT_ID_BACKGROUND_IMAGE);

        map_array_command.put(VsKeyConstants.ZT_COMMAND_MENU, VsKeyConstants.ZT_ID_MENU);

        // 需要在此添加你的config，否则找不到config
        register(new ToastConfig());
        register(new HelpConfig());
        register(new KnowConfig());
        register(new VersionConfig());
        register(new ForWardOpenRightConfig());
        register(new ForWardOpenLeftConfig());
        register(new XmlMenuConfig());
        register(new RebootConfig());
        register(new LnConfig());
        register(new AVncConfig());
        register(new X11CommandShowConfig());
        register(new X11CommandHideConfig());
        register(new X11StatusConfig());
        register(new X11KeyBoardShowConfig());
        register(new X11KeyBoardHideConfig());
        register(new ConfirmedDialogConfig());
        register(new QRCodeEncoderConfig());
        register(new VsBackgroundImageConfig());
    }
    private static void register(VsConfig ztConfig) {
        sparse_array_config.append(ztConfig.getId(), ztConfig);
    }

    public static VsConfig getConfig(String command) {
        Integer id = map_array_command.get(command);
        try {
            VsConfig ztConfig = sparse_array_config.get(id);
            if (ztConfig == null) {
                return new KnowConfig();
            }
            return ztConfig;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new KnowConfig();
    }

}
