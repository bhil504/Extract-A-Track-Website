from flask import Flask, request, jsonify
from spleeter.separator import Separator
import os
import sys

# Activate the virtual environment for Spleeter functionality
spleeter_env = 'C:/Users/bhill/spleeter_env/Scripts/activate_this.py'
exec(open(spleeter_env).read(), dict(__file__=spleeter_env))

app = Flask(__name__)

@app.route('/separate', methods=['POST'])
def separate():
    stems = request.form.get('stems')
    if stems not in ['2', '5']:
        return jsonify({'error': 'Invalid number of stems. Only 2 or 5 stems are supported.'}), 400

    separator = Separator(f'spleeter:{stems}stems')

    if 'file' not in request.files:
        return jsonify({'error': 'No file part'}), 400

    file = request.files['file']
    if file.filename == '':
        return jsonify({'error': 'No selected file'}), 400

    input_path = os.path.join('input', file.filename)
    output_path = os.path.join('output', os.path.splitext(file.filename)[0])
    os.makedirs('input', exist_ok=True)
    os.makedirs('output', exist_ok=True)
    file.save(input_path)

    try:
        separator.separate_to_file(input_path, 'output')
    except Exception as e:
        return jsonify({'error': f'Error separating file: {str(e)}'}), 500

    if stems == '2':
        stem_files = {
            'vocals': os.path.join(output_path, 'vocals.wav'),
            'accompaniment': os.path.join(output_path, 'accompaniment.wav')
        }
    else:  
        stem_files = {
            'vocals': os.path.join(output_path, 'vocals.wav'),
            'accompaniment': os.path.join(output_path, 'accompaniment.wav'),
            'bass': os.path.join(output_path, 'bass.wav'),
            'drums': os.path.join(output_path, 'drums.wav'),
            'piano': os.path.join(output_path, 'piano.wav'),
            'other': os.path.join(output_path, 'other.wav')
        }

    stem_files = {stem: path for stem, path in stem_files.items() if os.path.exists(path)}

    return jsonify({'status': 'success', 'output_paths': stem_files})

if __name__ == "__main__":
    app.run(port=5000, debug=True)
