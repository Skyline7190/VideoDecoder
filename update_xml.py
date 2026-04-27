import re

with open('app/src/main/res/layout/activity_main.xml', 'r', encoding='utf-8') as f:
    content = f.read()

# Add visibility gone to speed_card
content = content.replace(
    'android:id="@+id/speed_card"',
    'android:id="@+id/speed_card"\n                android:visibility="gone"'
)

# Add visibility gone to action_card
content = content.replace(
    'android:id="@+id/action_card"',
    'android:id="@+id/action_card"\n                android:visibility="gone"'
)

# Move ComposeView to be inside LinearLayout
compose_view_xml = """
            <!-- Liquid Glass Controls -->
            <androidx.compose.ui.platform.ComposeView
                android:id="@+id/compose_view"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="10dp" />
"""

# Find the end of progress_card
status_card_start = content.find('<com.google.android.material.card.MaterialCardView\n                android:id="@+id/status_card"')

if status_card_start != -1:
    content = content[:status_card_start] + compose_view_xml + content[status_card_start:]

# Remove the old ComposeView at the bottom
old_compose = """    <!-- Liquid Glass Floating Compose View Overlay -->
    <androidx.compose.ui.platform.ComposeView
        android:id="@+id/compose_view"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:layout_margin="16dp" />"""

content = content.replace(old_compose, "")

with open('app/src/main/res/layout/activity_main.xml', 'w', encoding='utf-8') as f:
    f.write(content)
