import re

with open('app/src/main/res/layout/activity_main.xml', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update LinearLayout padding (remove horizontal padding, keep top/bottom)
content = content.replace(
    'android:padding="16dp"\n            android:paddingBottom="100dp"', 
    'android:paddingTop="16dp"\n            android:paddingBottom="100dp"'
)

# 2. Add marginHorizontal to all cards to keep their original layout
cards = ['header_card', 'preview_card', 'progress_card', 'speed_card', 'action_card', 'status_card']
for card in cards:
    target = f'android:id="@+id/{card}"'
    replacement = f'android:id="@+id/{card}"\n                android:layout_marginHorizontal="16dp"'
    content = content.replace(target, replacement)

with open('app/src/main/res/layout/activity_main.xml', 'w', encoding='utf-8') as f:
    f.write(content)
