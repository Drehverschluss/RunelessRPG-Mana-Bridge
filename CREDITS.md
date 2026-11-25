# Credits

## RunelessRPG Mana Bridge

RunelessRPG Mana Bridge by Drehverschluss, licensed under the [MIT License](LICENSE). The license text is also in the root of the mod jar.

RunelessRPG Mana Bridge is a compatibility mod: it connects the mana of RunelessRPG (Spell Engine) and Iron's Spells 'n Spellbooks into one pool. It is not made by or affiliated with the authors of Iron's Spells 'n Spellbooks, Spell Engine or any other mod named here.

## Assets

The mod ships no files of other mods: no code, textures, models, sounds or translations. The logo and the banner were made for this mod. Everything else it shows (mana bars, tooltips, the spell wheel) is drawn by the mods themselves; the bridge only changes the numbers they show.

Other mods are only referenced by their classes, methods and ids:

- Iron's Spells 'n Spellbooks: its classes and methods are the targets of the bridge's own mixins (mana storage, cost check, regeneration, respawn rule, mana bar, tooltips, spell wheel, Inscription Table), its events (`SpellOnCastEvent`, `ChangeManaEvent`) are used, and its attributes `irons_spellbooks:max_mana` and `irons_spellbooks:mana_regen`, its HUD layer `irons_spellbooks:mana_overlay` and some entity ids in the two leech tags are used by id.
- RunelessRPG: only its public API (`com.drehverschluss.runelessrpg.api`), its attributes `runelessrpg:max_mana` and `runelessrpg:mana_regen` and its HUD layers by id.
- Spell Engine: its spell container is read to recognize spell weapons (for the visibility of Iron's bar).
- Other mana bridges: only their mod ids and, by reflection, their own config switches are read to see whether their mana function is on.

## Mods RunelessRPG Mana Bridge works with (not bundled)

RunelessRPG Mana Bridge is compiled against the mods below (compile only) and contains none of their files.

- [RunelessRPG](https://www.curseforge.com/minecraft/mc-mods/runelessrpg) by Drehverschluss (MIT): required, 0.4.0 or newer. The shared pool runs through its API. Not bundled.
- [Spell Engine](https://www.curseforge.com/minecraft/mc-mods/spell-engine) by Daedelus (GPL-3.0) and Spell Power Attributes by Daedelus (GPL-3.0): required by RunelessRPG. Compiled against them, not bundled.
- [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks) by Iron431 and Lab3 (All Rights Reserved): optional, tested with 1.21.1-3.16.4. Compiled against it together with Iron's Lib (All Rights Reserved). Not bundled; no code of it is copied.
- [Ars Nouveau](https://www.curseforge.com/minecraft/mc-mods/ars-nouveau) by Bailey Hollingsworth (GPL-3.0 according to its mod file): optional in the mod file, but this version has no adapter for it. Compiled against it for a later version; no code of it is used or copied.

## Other mana bridges (only detected)

The bridge recognizes these mods so that two bridges never pull at the same pool. No code, config or other file of them is used or copied; only their mod ids and their own switches are read.

- Ars 'n Spells (`ars_n_spells`, GPL-3.0)
- Ars Mana Bridge (`arsmanabridge`, MIT)
- Mana Transform (`mana_transform`, MIT)
- Unified Mana (`unifiedmana`, All Rights Reserved)

## License

RunelessRPG Mana Bridge is licensed under the [MIT License](LICENSE).
