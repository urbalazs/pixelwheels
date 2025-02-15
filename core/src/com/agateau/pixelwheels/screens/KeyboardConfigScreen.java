/*
 * Copyright 2019 Aurélien Gâteau <mail@agateau.com>
 *
 * This file is part of Pixel Wheels.
 *
 * Pixel Wheels is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.agateau.pixelwheels.screens;

import static com.agateau.translations.Translator.tr;

import com.agateau.pixelwheels.Constants;
import com.agateau.pixelwheels.GameConfig;
import com.agateau.pixelwheels.PwGame;
import com.agateau.pixelwheels.PwRefreshHelper;
import com.agateau.pixelwheels.gameinput.GameInputHandler;
import com.agateau.pixelwheels.gameinput.KeyboardInputHandler;
import com.agateau.ui.KeyMapper;
import com.agateau.ui.VirtualKey;
import com.agateau.ui.anchor.Anchor;
import com.agateau.ui.anchor.AnchorGroup;
import com.agateau.ui.menu.ButtonMenuItem;
import com.agateau.ui.menu.Menu;
import com.agateau.ui.menu.MenuItemListener;
import com.agateau.ui.uibuilder.UiBuilder;
import com.agateau.utils.Assert;
import com.agateau.utils.FileUtils;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import java.util.HashMap;

/** Configure a keyboard input device */
public class KeyboardConfigScreen extends PwStageScreen {
    private final PwGame mGame;
    private final int mPlayerIdx;
    private final Array<KeyMapper> mKeyMappers = new Array<>();
    private final HashMap<VirtualKey, ButtonMenuItem> mKeyButtonMap = new HashMap<>();
    private KeyMapper mKeyMapper;
    private Menu mMenu;

    private VirtualKey mEditedVirtualKey;

    KeyboardConfigScreen(PwGame game, int playerIdx) {
        super(game.getAssets().ui);
        mGame = game;
        mPlayerIdx = playerIdx;
        new PwRefreshHelper(mGame, getStage()) {
            @Override
            protected void refresh() {
                mGame.replaceScreen(new KeyboardConfigScreen(mGame, mPlayerIdx));
            }
        };

        // Find the current player KeyMapper, but also get all KeyMappers: we need them
        // to check for conflicts
        for (int idx = 0; idx < Constants.MAX_PLAYERS; ++idx) {
            GameInputHandler handler = mGame.getConfig().getPlayerInputHandler(idx);
            if (!(handler instanceof KeyboardInputHandler)) {
                continue;
            }
            KeyboardInputHandler keyboardInputHandler = (KeyboardInputHandler) handler;
            KeyMapper mapper = keyboardInputHandler.getKeyMapper();
            mKeyMappers.add(mapper);
            if (idx == playerIdx) {
                mKeyMapper = mapper;
            }
        }
        Assert.check(mKeyMapper != null, "no key mapper found for player " + playerIdx);

        setupUi();
    }

    private void setupUi() {
        UiBuilder builder = new UiBuilder(mGame.getAssets().atlas, mGame.getAssets().ui.skin);

        AnchorGroup root =
                (AnchorGroup) builder.build(FileUtils.assets("screens/keyboardconfig.gdxui"));
        root.setFillParent(true);
        getStage().addActor(root);

        mMenu = builder.getActor("menu");

        if (mPlayerIdx == 0) {
            // First player only configure in-game keys, others also configure UI keys
            createKeyItem(mMenu, tr("Brake"), VirtualKey.DOWN);
            createKeyItem(mMenu, tr("Steer left"), VirtualKey.LEFT);
            createKeyItem(mMenu, tr("Steer right"), VirtualKey.RIGHT);
            createKeyItem(mMenu, tr("Trigger"), VirtualKey.TRIGGER);
        } else {
            createKeyItem(mMenu, tr("Up"), "-", VirtualKey.UP);
            createKeyItem(mMenu, tr("Down"), tr("Brake"), VirtualKey.DOWN);
            createKeyItem(mMenu, tr("Left"), tr("Steer left"), VirtualKey.LEFT);
            createKeyItem(mMenu, tr("Right"), tr("Steer right"), VirtualKey.RIGHT);
            createKeyItem(mMenu, tr("Activate"), tr("Trigger"), VirtualKey.TRIGGER);
            createKeyItem(mMenu, tr("Back"), "-", VirtualKey.BACK);
        }

        builder.getActor("backButton")
                .addListener(
                        new ClickListener() {
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                onBackPressed();
                            }
                        });
    }

    private void createKeyItem(Menu menu, String text1, String text2, VirtualKey virtualKey) {
        ButtonMenuItem button = new ButtonMenuItem(menu, getButtonText(virtualKey));
        button.addListener(
                new MenuItemListener() {
                    @Override
                    public void triggered() {
                        startEditing(button, virtualKey);
                    }
                });

        if (text2 == null) {
            menu.addItemWithLabel(text1, button);
        } else {
            AnchorGroup group = new AnchorGroup();
            group.addPositionRule(
                    new Label(text1, menu.getSkin()),
                    Anchor.CENTER_LEFT,
                    group,
                    Anchor.CENTER_LEFT);
            group.addPositionRule(
                    new Label(text2, menu.getSkin()), Anchor.CENTER_LEFT, group, Anchor.CENTER);
            menu.addItemWithLabelActor(group, button);
        }
        mKeyButtonMap.put(virtualKey, button);
    }

    private void createKeyItem(Menu menu, String text, VirtualKey virtualKey) {
        createKeyItem(menu, text, null, virtualKey);
    }

    private final InputListener mEditListener =
            new InputListener() {
                public boolean keyUp(InputEvent event, int keycode) {
                    Assert.check(mEditedVirtualKey != null, "mEditVirtualKey should be set");

                    if (keycode != Input.Keys.ESCAPE) {
                        updateKey(keycode);
                    }
                    updateButtonText(mEditedVirtualKey);

                    stopEditing();
                    return true;
                }
            };

    private void updateKey(int newKey) {
        int oldKey = mKeyMapper.getKey(mEditedVirtualKey);
        mKeyMapper.setKey(mEditedVirtualKey, newKey);
        checkConflicts(oldKey, newKey);
    }

    private void checkConflicts(int oldKey, int newKey) {
        for (KeyMapper keyMapper : mKeyMappers) {
            for (VirtualKey virtualKey : VirtualKey.values()) {
                if (keyMapper == mKeyMapper && virtualKey == mEditedVirtualKey) {
                    // Skip ourselves
                    continue;
                }

                int key = keyMapper.getKey(virtualKey);
                if (key == newKey) {
                    // We found a conflict
                    keyMapper.setKey(virtualKey, oldKey);
                    if (keyMapper == mKeyMapper) {
                        // Conflict with the current key mapper: update the UI
                        updateButtonText(virtualKey);
                    }
                }
            }
        }
    }

    private void startEditing(ButtonMenuItem button, VirtualKey virtualKey) {
        mMenu.setDisabled(true);
        mEditedVirtualKey = virtualKey;

        button.setText("...");
        getStage().getRoot().addListener(mEditListener);
    }

    private void stopEditing() {
        mMenu.setDisabled(false);
        getStage().getRoot().removeListener(mEditListener);
        mEditedVirtualKey = null;
    }

    private void updateButtonText(VirtualKey key) {
        ButtonMenuItem button = mKeyButtonMap.get(key);
        button.setText(getButtonText(key));
    }

    private String getButtonText(VirtualKey virtualKey) {
        int key = mKeyMapper.getKey(virtualKey);
        return Input.Keys.toString(key);
    }

    @Override
    public void onBackPressed() {
        saveConfig();
        mGame.popScreen();
    }

    private void saveConfig() {
        GameConfig config = mGame.getConfig();
        config.savePlayerInputHandlerConfig(mPlayerIdx);
    }
}
