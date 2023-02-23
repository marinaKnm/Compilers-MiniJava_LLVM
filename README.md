# Compilers-MiniJava_LLVM

- Τα αρχεία .ll δημιουργούνται στο ίδιον φάκελο που βρίσκονται τα αρχεία .java τα οποία δίνονται ως είσοδος στο πρόγραμμα.

- Τα αρχεία .jar δεν περιέχονται στον φάκελο ωστόσο στο Makefile περιέχονται στις εντολές.

- Θεωρείται ότι τα προγράμματα προς μεταγλώττιση είναι semantically correct καθώς αυτό ήταν το ζητούμενο της 2ης Εργασίας.

- Καλούνται δύο visitors στην Main:
  1) DeclCollector (της Εργασίας 2)
  2) Generator

- Στον DeclCollector προστίθενται δομές:
  -  Maps οι οποίες θα κρατάνε τα offsets συγκεκριμένα:
      - ένα map για τις μεταβλητές των κλάσεων με κλειδί το όνομα της κλάσης και τιμή ένα άλλο map το οποίο έχει κλειδί 
        το όνομα της μεταβλητής και τιμή το offset της
      - ένα map για τις μεθόδους των κλάσεων με κλειδί το όνομα της κλάσης και τιμή ένα άλλο map το οποίο έχει κλειδί 
        το όνομα της μεθόδου και τιμή το offset της
          - να σημειώσουμε ότι οι μέθοδοι που είναι οverriden ορίζονται με offset = null
      - οι δομές αυτές δημιουργούνται με την μέθοδο create_offsets, του visitor DeclCollector, η οποία βασίζεται στις δομές 
        που υλοποιήθηκαν στην Εργασία 2   
  - map (class_method_args) το οποίο κρατά τα ορίσματα μιας συνάρτησης πρόκειται δηλ. για map 3 επιπέδων, πιο συγκεκριμένα:
    - έχει κλειδί όνομα κλάσης το οποίο δείχνει σε ένα map με κλειδί όνομα μεθόδου το οποίο με τη σειρά του δείχνει σε ένα άλλο 
      map <όνομα μεταβλητής, τύπος> 
    - Η δομή class_method_args θα μας βοηθήσει στην δημιουργία των v-tables των κλάσεων αργότερα.

- Στον DeclCollector προστέθηκαν οι λειτουργίες:
  - create_offsets() όπως προαναφέρθηκε
  - print_offsets() η οποία βάσει των δομών των offsets εκτυπώνει εκτυπώνει τα offsets όπως είχε ζητηθεί στην Εργασία 2
  - isOverriden() η οποία βάσει του ονόματος μιας μεθόδου και της κλάσης, στην οποία ανήκει, ψάχνει στις δομές αν για όλες τις 
    κλάσεις από τις οποίες κληρονομεί υπάρχει μέθοδος με το ίδιο όνομα
      - επειδή τα προγράμματα που δίνονται στην είσοδο θεωρούνται semantically correct το όνομα αρκεί για να διακρίνουμε αν μια
        κλάση είναι overriden
      - η λειτουργία isOverriden() χρησιμεύει στην λειτουργία create_offsets() για να θέσουμε offset = null στις overriden μεθόδους

- Η παραγωγή κώδικα γίνεται στο αρχείο Generator.java στο οποίο ορίζεται ο visitor Generator. Εδώ υλοποιείται το ζητούμενο της Εργασίας 3.
  - Τύποι:
    - ακέραιοι -> i32
    - boolean -> i1
    - πίνακας ακεραίων -> struct το οποίο ορίζεται σε κάθε αρχείο ονόματι %int_arr*
      - ορίζεται ως type { i32, i32* } όπου το πρώτο αντιστοιχεί στο μέγεθος του πίνακα και το 2ο στον πραγματικό πίνακα
    - πίνακας boolean -> struct το οποίο ορίζεται σε κάθε αρχείο ονόματι %bool_arr*
      - ορίζεται ως type { i32, i1* } όπου το πρώτο αντιστοιχεί στο μέγεθος του πίνακα και το 2ο στον πραγματικό πίνακα
    - κλάσεις -> i8*

  - Πεδία κλάσης Generator:
    - αντικείμενο τύπου DeclCollector για να έχουμε πρόσβαση στις δομές
    - όνομα του παραγόμενου .ll αρχείου 
    - current_class και current_method για να ξέρουμε στην εκτέλεση των visitors ποια κλάση και ποια μέθοδο αναλύουμε
    - μετρητές reg_counter και label_counter για παραγωγή μοναδικών ονομάτων registers και labels
    - λίστα ονόματι ExpressionList η οποία παρέχει τα ορίσματα για κάθε κλήση συνάρτησης κατά την εκτέλεση της visit στον κόμβο MessageSend
    - map ονόματι object_class το οποίο έχει ως κλειδί όνομα καταχωρητή ο οποίος κρατά διέθυνση αντικειμένου μιας οποιοδήποτε κλάσης και τιμή το όνομα
      της κλάσης
    - map ονόματι reg_array το οποίο έχει ως κλειδί όνομα καταχωρητή ο οποίος κρατά διέθυνση δεσμευμένου πίνακα ενός οποιουδήποτε πίνακα και τιμή τον τύπο
      array (%int_arr* ή %bool_arr*)

  - Ο constuctor Generator() αναλαμβάνει να:
    - αρχικοποιήσει πεδία και δομές
    - δημιουργήσει το αρχείο .ll
    - γράψει τους πίνακες v-table για κάθε κλάση
      - με την μέθοδο find_number_of_functions() με όρισμα το όνομα της εκάστοτε κλάσης βρίσκουμε το μέγεθος που πρέπει να 
        έχει το v-table
        - η find_number_of_functions() είναι μια αναδρομική συνάρτηση η οποία ψάχνει στην κληρονομικότητα κλάσεων για μοναδικές συναρτήσεις
      - αρχικά, για κάθε κλάση, ο πίνακας δημιουργείται ως πίνακας από strings στον οποίο γράφει η συνάρτηση create_vtable() για την εκάστοτε κλάση
        - η  create_vtable() αναδρομικά πηγαίνει στην κορυφαία υπερκλάση και για κάθε κλάση γράφει τις συναρτήσεις στον πίνακα των strings. Σε περίπτωση
          που προκύψει overriden συνάρτηση πηγαίνει στον αντίστοιχο index του πίνακα (οffset/8) και ανανεώνει με την νέα μέθοδο.
      - μετά την κλήση της create_vtable() ο πίνακας αυτός αντιγράφεται στον αρχείο
    - τέλος, γράφει κάποιες εντολές που είναι χρήσιμες σε κάθε αρχείο όπως συναρτήσεις για εκτύπωση ακεραίου, για εκτύπωση μηνυμάτων και δήλωση τύπων 
      πινάκων

  - Οι λειτουργίες find_method_offset() και find_field_offset() είνα αναδρομικές συναρτήσεις οι οποίες ψάχνουν για offset μεθόδων και πεδίων, αντίστοιχα,
    στις δομές του DeclCollector.

  - Η μέθοδος get_object_size() είναι επίσης μια αναδρομική μέθοδος η οποία βρίσκει το μέγεθος που θα έχει ένα αντικείμενο μια συγκεκριμένης κλάσης.

  - Η μέθοδος get_function_args() επιστρέφει το map με τα ορίσματα μιας συνάρτησης και τον τύπο τους. 

  - Η μέθοδος get_return_type() επιστρέφει τον τύπο επιστροφής μιας συγκεκριμένης μεθόδου.

  - Η μέθοδος find_type() επιστρέφει τον τύπο επιστροφής μιας ενός πεδίου για μία κλάση.

  - Κατά την εκτέλεση συναρτήσεων visit χρειάζεται να ξέρουμε αν μια μεταβλητή είναι τοπική μεταβλητή ή πεδίο κλάσης για αυτό το σκοπό έχουν οριστεί οι
    isLocalVar() και isField(), αντίστοιχα, οι οποίες επιστρέφουν boolean.

  - H visit του κόμβου Expression επιστρέφει το string που αντιστοιχεί στον register που κρατά το αποτέλεσμα ή την τιμή την ίδια.

  - Η μέθοδος convert_to_register() χρησιμοποιείται για κάθε μεταβλητή που θέλουμε να χρησιμοποιήσουμε προκειμένου να γράψουμε εντολές με το όνομα του 
    καταχωρητή που θα επιστρέψει. Ανάλογα με το όρισμα (τοπική μεταβλητή/πεδίο ή λέξη this) θα γράψει τις κατάλληλες εντολές για load και επιστρέφει τον
    καταχωρητή.

  - Στην δημιουργία πίνακα γράφονται εντολές για εκτύπωση λάθους σε περίπτωση που το μέγεθος είναι αρνητικό.

  - Σε κάθε πρόσβαση σε πίνακα γράφονται εντολές για εκτύπωση λάθους σε περίπτωση που το index είναι εκτός των ορίων του πίνακα.
