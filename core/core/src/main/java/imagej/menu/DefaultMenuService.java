/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2014 Board of Regents of the University of
 * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
 * Institute of Molecular Cell Biology and Genetics.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package imagej.menu;

import imagej.module.ModuleInfo;
import imagej.module.ModuleService;
import imagej.module.event.ModulesAddedEvent;
import imagej.module.event.ModulesRemovedEvent;
import imagej.module.event.ModulesUpdatedEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.scijava.UIDetails;
import org.scijava.event.EventHandler;
import org.scijava.event.EventService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.Service;

/**
 * Default service for keeping track of the application's menu structure.
 * 
 * @author Curtis Rueden
 * @see ShadowMenu
 */
@Plugin(type = Service.class)
public class DefaultMenuService extends AbstractService implements MenuService
{

	@Parameter
	private EventService eventService;

	@Parameter
	private ModuleService moduleService;

	/** Menu tree structures. There is one structure per menu root. */
	private HashMap<String, ShadowMenu> rootMenus;

	// -- MenuService methods --

	@Override
	public ShadowMenu getMenu() {
		return getMenu(UIDetails.APPLICATION_MENU_ROOT);
	}

	@Override
	public ShadowMenu getMenu(final String menuRoot) {
		return rootMenus().get(menuRoot);
	}

	@Override
	public <T> T createMenus(final MenuCreator<T> creator, final T menu) {
		return createMenus(UIDetails.APPLICATION_MENU_ROOT, creator, menu);
	}

	@Override
	public <T> T createMenus(final String menuRoot,
		final MenuCreator<T> creator, final T menu)
	{
		creator.createMenus(getMenu(menuRoot), menu);
		return menu;
	}

	// -- Event handlers --

	@EventHandler
	protected void onEvent(final ModulesAddedEvent event) {
		if (rootMenus == null) {
			// add *all* known modules, which includes the ones given here
			rootMenus();
			return;
		}
		// data structure already exists; add *these* modules only
		addModules(event.getItems());
	}

	@EventHandler
	protected void onEvent(final ModulesRemovedEvent event) {
		for (final ShadowMenu menu : rootMenus().values()) {
			menu.removeAll(event.getItems());
		}
	}

	@EventHandler
	protected void onEvent(final ModulesUpdatedEvent event) {
		for (final ShadowMenu menu : rootMenus().values()) {
			menu.updateAll(event.getItems());
		}
	}

	// -- Helper methods --

	/**
	 * Adds the given collection of modules to the menu data structure.
	 * <p>
	 * The menu data structure is created lazily via {@link #rootMenus()} if it
	 * does not already exist. Note that this may result in a recursive call to
	 * this method to populate the menus with the collection of modules currently
	 * known by the {@link ModuleService}.
	 * </p>
	 */
	private synchronized void addModules(final Collection<ModuleInfo> items) {
		// categorize modules by menu root
		final HashMap<String, ArrayList<ModuleInfo>> modulesByMenuRoot =
			new HashMap<String, ArrayList<ModuleInfo>>();
		for (final ModuleInfo info : items) {
			final String menuRoot = info.getMenuRoot();
			ArrayList<ModuleInfo> modules = modulesByMenuRoot.get(menuRoot);
			if (modules == null) {
				modules = new ArrayList<ModuleInfo>();
				modulesByMenuRoot.put(menuRoot, modules);
			}
			modules.add(info);
		}

		// process each menu root separately
		for (final String menuRoot : modulesByMenuRoot.keySet()) {
			final ArrayList<ModuleInfo> modules = modulesByMenuRoot.get(menuRoot);
			ShadowMenu menu = rootMenus().get(menuRoot);
			if (menu == null) {
				// new menu root: create new menu structure
				menu = new ShadowMenu(getContext(), modules);
				rootMenus().put(menuRoot, menu);
			}
			else {
				// existing menu root: add to menu structure
				menu.addAll(modules);
			}
		}

	}

	/**
	 * Lazily creates the {@link #rootMenus} data structure.
	 * <p>
	 * Note that the data structure is initially populated with all modules
	 * available from the {@link ModuleService}, which is accomplished via a call
	 * to {@link #addModules(Collection)}, which calls {@link #rootMenus()}, which
	 * can result in a level of recursion. This is intended.
	 * </p>
	 */
	private HashMap<String, ShadowMenu> rootMenus() {
		if (rootMenus == null) {
			rootMenus = new HashMap<String, ShadowMenu>();

			final List<ModuleInfo> allModules = moduleService.getModules();
			addModules(allModules);
		}
		return rootMenus;
	}

}
