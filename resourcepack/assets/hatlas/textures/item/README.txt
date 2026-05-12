Place your 16×16 (or larger, must be square) PNG textures here:

  ruby.png
  ruby_block.png
  ruby_ore.png
  deepslate_ruby_ore.png

  amethyst_sword.png
  amethyst_pickaxe.png
  amethyst_axe.png
  amethyst_shovel.png
  amethyst_hoe.png
  amethyst_spear.png
  amethyst_helmet.png
  amethyst_chestplate.png
  amethyst_leggings.png
  amethyst_boots.png
  amethyst_horse_armor.png

Each one is referenced by the matching .json file in assets/hatlas/models/item/
as "hatlas:item/<name>" which resolves to assets/hatlas/textures/item/<name>.png.

For armor that renders correctly when WORN (not just in inventory), see README.md
section "Custom armor on-body textures" — that requires either:
  (a) the new equippable component (1.21.2+) with a per-piece equipment_asset, OR
  (b) overlay textures placed in assets/minecraft/textures/models/armor/.

The amethyst armor set uses the equippable component and references the
asset id "hatlas:amethyst" (humanoid) and "hatlas:amethyst_horse" (horse body),
declared in assets/hatlas/equipment/amethyst.json and amethyst_horse.json.
