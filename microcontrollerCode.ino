const byte ROWS = 8;
const byte COLS = 8;

const byte rowPins[ROWS] = {10, 11, 12, 13, A0, A1, A2, A3};
const byte colPins[COLS] = {2, 3, 4, 5, 6, 7, 8, 9};

// Use lowercase labels for lowercase output like "e8"
const char colLabels[COLS] = {'H', 'G', 'F', 'E', 'D', 'C', 'B', 'A'};
const char rowLabels[ROWS] = {'1', '2', '3', '4', '5', '6', '7', '8'};

void setup() {
  Serial.begin(9600);

  for (byte r = 0; r < ROWS; r++) {
    pinMode(rowPins[r], INPUT_PULLUP);
  }

  for (byte c = 0; c < COLS; c++) {
    pinMode(colPins[c], OUTPUT);
    digitalWrite(colPins[c], HIGH);
  }

  //Serial.println("Chess Matrix Ready");
}

void loop() {
  for (byte c = 0; c < COLS; c++) {
    digitalWrite(colPins[c], LOW);

    for (byte r = 0; r < ROWS; r++) {
      if (digitalRead(rowPins[r]) == LOW) {
        // char file = colLabels[c];    // 'a' to 'h'
        // char rank = rowLabels[r];    // '1' to '8'

        // Serial.print("Move detected at: ");
        Serial.print(String(colLabels[c]) + String(rowLabels[r]));
        delay(300); // Debounce
      }
    }

    digitalWrite(colPins[c], HIGH);
    delay(1);
  }
}
