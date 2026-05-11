Place your 16×16 (or larger, must be square) PNG textures here:

  ruby.png
  ruby_ore.png
  ruby_sword.png
  ruby_pickaxe.png
  ruby_axe.png
  ruby_shovel.png
  ruby_helmet.png
  ruby_chestplate.png
  ruby_leggings.png
  ruby_boots.png

Each one is referenced by the matching .json file in assets/hatlas/models/item/
as "hatlas:item/<name>" which resolves to assets/hatlas/textures/item/<name>.png.

For armor that renders correctly when WORN (not just in inventory), see README.md
section "Custom armor on-body textures" — that requires either:
  (a) the new equippable component (1.21.2+) with a per-piece equipment_asset, OR
  (b) overlay textures placed in assets/minecraft/textures/models/armor/.
